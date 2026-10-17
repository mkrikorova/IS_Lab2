package ru.itmo.spacemarine.tx;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Выполняет работу в транзакции SERIALIZABLE и повторяет её, если PostgreSQL
 * отменил транзакцию из-за конфликта с параллельной (SQLSTATE 40001).
 *
 * Где используется — операции записи, корректность которых зависит от проверки
 * «таких строк ещё нет» (ограничения уникальности): создание/изменение ордена,
 * координат, десантника, спецоперация «создать орден», импорт.
 *
 * Схема:
 * <pre>
 *   сервис (вне транзакции, @Transactional NOT_SUPPORTED)
 *     └─ executor.execute(work)          цикл повторов — снаружи транзакции
 *          └─ runner.run(work)           REQUIRES_NEW: begin
 *               ├─ SET TRANSACTION ISOLATION LEVEL SERIALIZABLE
 *               ├─ work: проверки уникальности + INSERT/UPDATE
 *               └─ commit  (здесь PostgreSQL тоже может обнаружить конфликт)
 * </pre>
 * Повтор обязателен: SERIALIZABLE не ставит транзакции в очередь, а отменяет одну из
 * конфликтующих. Отменённая транзакция ничего не записала, поэтому её безопасно выполнить
 * заново — при повторе она видит данные конкурента и, например, отвечает 409 «уже существует».
 *
 * Каждая попытка вызывается через прокси runner'а — это новая транзакция со всеми перехватчиками.
 */
@ApplicationScoped
public class SerializableExecutor {

    private static final Logger LOG = Logger.getLogger(SerializableExecutor.class.getName());
    static final int MAX_ATTEMPTS = 8;

    @Inject
    private SerializableTxRunner runner;
    @Inject
    private IsolationSupport isolation;

    public <T> T execute(String operation, Supplier<T> work) {
        if (isolation.inTransaction()) {
            // вызвали изнутри уже идущей транзакции — работаем в ней, повторять будет внешний уровень
            return work.get();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return runner.run(work);
            } catch (RuntimeException e) {
                if (!IsolationSupport.isRetryable(e)) {
                    throw e; // бизнес-ошибка (409, 403, 400…) — не повторяем
                }
                if (attempt >= MAX_ATTEMPTS) {
                    LOG.log(Level.WARNING, operation + ": конфликт сериализации не разрешился за "
                            + MAX_ATTEMPTS + " попыток", e);
                    throw e;
                }
                LOG.info(operation + ": конфликт сериализации (" + IsolationSupport.describe(e) + "), повтор "
                        + (attempt + 1) + " из " + MAX_ATTEMPTS);
                pause(attempt);
            }
        }
    }

    /** Случайная пауза, растущая с номером попытки, — конкуренты расходятся во времени. */
    private static void pause(int attempt) {
        long max = Math.min(25L << attempt, 1000L);
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(max / 2, max + 1));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
