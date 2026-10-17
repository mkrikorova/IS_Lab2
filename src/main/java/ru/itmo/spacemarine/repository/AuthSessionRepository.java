package ru.itmo.spacemarine.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ru.itmo.spacemarine.model.AuthSession;

import java.time.ZonedDateTime;
import java.util.Optional;

@ApplicationScoped
public class AuthSessionRepository {

    @PersistenceContext(unitName = "spacemarinePU")
    private EntityManager em;

    /** Действующая (не истёкшая) сессия по хэшу токена, сразу вместе с пользователем. */
    public Optional<AuthSession> findActive(String tokenHash, ZonedDateTime now) {
        return em.createQuery(
                        "SELECT s FROM AuthSession s JOIN FETCH s.user "
                                + "WHERE s.tokenHash = :hash AND s.expiresAt > :now", AuthSession.class)
                .setParameter("hash", tokenHash)
                .setParameter("now", now)
                .getResultStream()
                .findFirst();
    }

    public void save(AuthSession session) {
        em.persist(session);
    }

    public int delete(String tokenHash) {
        return em.createQuery("DELETE FROM AuthSession s WHERE s.tokenHash = :hash")
                .setParameter("hash", tokenHash)
                .executeUpdate();
    }

    public int deleteExpired(ZonedDateTime now) {
        return em.createQuery("DELETE FROM AuthSession s WHERE s.expiresAt <= :now")
                .setParameter("now", now)
                .executeUpdate();
    }
}
