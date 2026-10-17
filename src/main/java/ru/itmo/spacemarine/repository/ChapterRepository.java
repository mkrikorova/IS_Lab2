package ru.itmo.spacemarine.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import ru.itmo.spacemarine.model.Chapter;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ChapterRepository {

    @PersistenceContext(unitName = "spacemarinePU")
    private EntityManager em;

    public Optional<Chapter> findById(Integer id) {
        return Optional.ofNullable(em.find(Chapter.class, id)); // ищет по первичному ключу и явно обрабатывает ответ в виде null
    }

    public List<Chapter> findAll() {
        return em.createQuery("SELECT c FROM Chapter c ORDER BY c.id", Chapter.class).getResultList();
    }

    public Chapter save(Chapter chapter) {
        if (chapter.getId() == null) {
            em.persist(chapter);
            em.flush(); // прям щас в  бд, чтобы сразу получить id и поймать ошибки ограничений
            return chapter;
        }
        return em.merge(chapter); //копирует состояние объекта в управляемую копию
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

    public void delete(Chapter chapter) {
        em.remove(em.contains(chapter) ? chapter : em.merge(chapter));
    }

    /**
     * Кандидаты для проверки уникальности названия: ордены с названием той же длины.
     * Окончательное сравнение без учёта регистра делается в Java (см. service.Names).
     */
    public List<Chapter> findByNameLength(int length) {
        return em.createQuery("SELECT c FROM Chapter c WHERE LENGTH(c.name) = :len ORDER BY c.id", Chapter.class)
                .setParameter("len", length)
                .getResultList();
    }

    /** Сколько десантников ссылается на орден. */
    public long countMarines(Integer chapterId) {
        return em.createQuery("SELECT COUNT(m) FROM SpaceMarine m WHERE m.chapter.id = :id", Long.class)
                .setParameter("id", chapterId)
                .getSingleResult();
    }

    /** Перепривязать всех десантников с одного ордена на другой. */
    public int reassignMarines(Integer fromId, Integer toId) {
        // массовый UPDATE идёт в обход @Version — увеличиваем версию явно,
        // иначе параллельное изменение перепривязанного десантника не заметит конфликта
        return em.createQuery("UPDATE SpaceMarine m SET m.chapter = :to, m.version = m.version + 1 WHERE m.chapter.id = :from")
                .setParameter("to", em.getReference(Chapter.class, toId))
                .setParameter("from", fromId)
                .executeUpdate();
    }
}
