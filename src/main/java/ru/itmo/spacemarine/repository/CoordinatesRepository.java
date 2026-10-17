package ru.itmo.spacemarine.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import ru.itmo.spacemarine.model.Coordinates;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CoordinatesRepository {

    @PersistenceContext(unitName = "spacemarinePU")
    private EntityManager em;

    public Optional<Coordinates> findById(Integer id) {
        return Optional.ofNullable(em.find(Coordinates.class, id));
    }

    public List<Coordinates> findAll() {
        return em.createQuery("SELECT c FROM Coordinates c ORDER BY c.id", Coordinates.class).getResultList();
    }

    public Coordinates save(Coordinates coordinates) {
        if (coordinates.getId() == null) {
            em.persist(coordinates);
            em.flush();
            return coordinates;
        }
        return em.merge(coordinates);
    }

    /**
     * При сохранении версия увеличивается ВСЕГДА, даже если поля не изменились.
     * Без этого «сохранение без изменений» не выполняет UPDATE вовсе, версия не проверяется,
     * и параллельное изменение другого пользователя не обнаруживается (так было в JMeter:
     * admin1 сохранил исходное health=100, admin2 — 200, оба получили 200).
     */
    public void forceVersionIncrement(Object entity) {
        em.lock(entity, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    /** Выполнить отложенные UPDATE/DELETE сейчас, чтобы конфликт версий проявился внутри метода сервиса. */
    public void flush() {
        em.flush();
    }

    public void delete(Coordinates coordinates) {
        em.remove(em.contains(coordinates) ? coordinates : em.merge(coordinates));
    }

    /** Точка с такими же (x, y), кроме excludeId (null — без исключения). */
    public Optional<Coordinates> findByXY(long x, double y, Integer excludeId) {
        String jpql = "SELECT c FROM Coordinates c WHERE c.x = :x AND c.y = :y"
                + (excludeId != null ? " AND c.id <> :exclude" : "")
                + " ORDER BY c.id";
        TypedQuery<Coordinates> q = em.createQuery(jpql, Coordinates.class)
                .setParameter("x", x)
                .setParameter("y", y);
        if (excludeId != null) q.setParameter("exclude", excludeId);
        return q.setMaxResults(1).getResultStream().findFirst();
    }

    public long countMarines(Integer coordinatesId) {
        return em.createQuery("SELECT COUNT(m) FROM SpaceMarine m WHERE m.coordinates.id = :id", Long.class)
                .setParameter("id", coordinatesId)
                .getSingleResult();
    }

    public int reassignMarines(Integer fromId, Integer toId) {
        // массовый UPDATE идёт в обход @Version — увеличиваем версию явно
        return em.createQuery("UPDATE SpaceMarine m SET m.coordinates = :to, m.version = m.version + 1 WHERE m.coordinates.id = :from")
                .setParameter("to", em.getReference(Coordinates.class, toId))
                .setParameter("from", fromId)
                .executeUpdate();
    }
}
