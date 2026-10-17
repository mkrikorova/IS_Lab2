package ru.itmo.spacemarine.tx;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.function.Supplier;

/**
 * Одна попытка: новая транзакция с уровнем SERIALIZABLE.
 * REQUIRES_NEW + вызов через прокси этого бина (из SerializableExecutor) гарантируют,
 * что каждая попытка — полноценная отдельная транзакция: begin, SET TRANSACTION, работа, commit.
 */
@ApplicationScoped
public class SerializableTxRunner {

    @Inject
    private IsolationSupport isolation;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public <T> T run(Supplier<T> work) {
        isolation.ensureSerializable(); // первый оператор транзакции
        return work.get();
    }
}
