package ru.itmo.spacemarine.tx;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.TransactionSynchronizationRegistry;

import java.sql.SQLException;

/** Работа с текущей JTA-транзакцией: уровень изоляции и распознавание конфликтов сериализации. */
@ApplicationScoped
public class IsolationSupport {

    private static final String SERIALIZABLE_KEY = IsolationSupport.class.getName() + ".serializable";

    @PersistenceContext(unitName = "spacemarinePU")
    private EntityManager em;

    @Resource
    private TransactionSynchronizationRegistry registry;

    public boolean inTransaction() {
        return registry.getTransactionKey() != null;
    }

    /**
     * Перевести текущую транзакцию в SERIALIZABLE. В PostgreSQL SET TRANSACTION должен быть
     * первым оператором транзакции, поэтому SerializableTxRunner вызывает его сразу после begin.
     * Действует только до конца этой транзакции — соединение возвращается в пул
     * с уровнем по умолчанию (READ COMMITTED), остальные операции не затрагиваются.
     * Повторный вызов в той же транзакции ничего не делает.
     */
    public void ensureSerializable() {
        if (!inTransaction()) {
            throw new IllegalStateException("SERIALIZABLE можно включить только внутри транзакции");
        }
        if (registry.getResource(SERIALIZABLE_KEY) != null) {
            return;
        }
        em.createNativeQuery("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE").executeUpdate();
        registry.putResource(SERIALIZABLE_KEY, Boolean.TRUE);
    }

    /**
     * Транзакцию отменили из-за параллельной транзакции, и её можно безопасно повторить:
     * <ul>
     *   <li>SQLSTATE 40001 serialization_failure («could not serialize access …»), 40P01 deadlock_detected —
     *       если конфликт обнаружен на операторе;</li>
     *   <li>25001 / 25P02 — соединение досталось в незавершённом состоянии после сбоя фиксации
     *       предыдущей транзакции (найдено по логу Payara после прогона JMeter);</li>
     *   <li>сбой фиксации (RollbackException / TransactionalException / SystemException) —
     *       PostgreSQL может обнаружить конфликт сериализации только в момент COMMIT, а менеджер
     *       транзакций сообщает об этом своим исключением, не всегда сохраняя SQLException внутри.</li>
     * </ul>
     * Не повторяем: конфликт версии (OptimisticLock — клиент работал с устаревшими данными, ему 409)
     * и прикладные исключения (их TransactionalInterceptor пробрасывает как есть).
     */
    public static boolean isRetryable(Throwable e) {
        boolean commitFailure = false;
        for (Throwable t = e; t != null; t = next(t)) {
            String name = t.getClass().getSimpleName();
            if (name.contains("OptimisticLock")) {
                return false;
            }
            if (t instanceof SQLException sql) {
                String state = sql.getSQLState();
                if ("40001".equals(state) || "40P01".equals(state)) {
                    return true;
                }
                // 25001 «SET TRANSACTION ISOLATION LEVEL must be called before any query»,
                // 25P02 «current transaction is aborted»: попытка получила соединение, оставшееся
                // в незавершённом состоянии после конфликта, обнаруженного при COMMIT
                // (в логе Payara перед этим RAR5031 … could not serialize access … during commit attempt).
                // Наша попытка к этому моменту ничего не сделала — новая транзакция безопасна.
                if ("25001".equals(state) || "25P02".equals(state)) {
                    return true;
                }
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("could not serialize access") || msg.contains("deadlock detected"))) {
                return true;
            }
            if (name.equals("RollbackException") || name.equals("TransactionalException")
                    || name.equals("SystemException") || name.equals("HeuristicRollbackException")) {
                commitFailure = true;
            }
        }
        return commitFailure;
    }

    /** Цепочка классов исключения для журнала: «TransactionalException ← RollbackException ← PSQLException». */
    public static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = next(t)) {
            if (sb.length() > 0) sb.append(" ← ");
            sb.append(t.getClass().getSimpleName());
            if (t instanceof SQLException sql) sb.append('[').append(sql.getSQLState()).append(']');
        }
        return sb.toString();
    }

    private static Throwable next(Throwable t) {
        Throwable c = t.getCause();
        if (c == null && t instanceof SQLException sql) {
            c = sql.getNextException();
        }
        return c == t ? null : c;
    }
}
