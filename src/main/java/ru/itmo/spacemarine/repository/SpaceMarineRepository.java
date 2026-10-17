package ru.itmo.spacemarine.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import ru.itmo.spacemarine.dto.SpaceMarineFilter;
import ru.itmo.spacemarine.model.MeleeWeapon;
import ru.itmo.spacemarine.model.SpaceMarine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SpaceMarineRepository {

    @PersistenceContext(unitName = "spacemarinePU")
    private EntityManager em;

    public Optional<SpaceMarine> findById(Integer id) {
        return Optional.ofNullable(em.find(SpaceMarine.class, id));
    }

    public SpaceMarine save(SpaceMarine marine) {
        if (marine.getId() == null) {
            em.persist(marine);
            em.flush();
            return marine;
        }
        return em.merge(marine);
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

    public void delete(SpaceMarine marine) {
        em.remove(em.contains(marine) ? marine : em.merge(marine));
    }

    /**
     * Кандидаты в полные дубли: совпадают все поля, кроме id, creationDate и имени,
     * а имя — той же длины. Имя без учёта регистра сравнивается уже в Java
     * (см. UniquenessService.findDuplicate и service.Names).
     * Запрос собирается динамически, потому что сравнение с NULL в SQL
     * пишется как IS NULL (условие «heart_count = NULL» никогда не истинно).
     */
    public List<SpaceMarine> findDuplicateCandidates(SpaceMarine m, int nameLength, Integer excludeId) {
        StringBuilder jpql = new StringBuilder(
                "SELECT m FROM SpaceMarine m WHERE LENGTH(m.name) = :nameLength"
                        + " AND m.coordinates.id = :coordinatesId AND m.chapter.id = :chapterId"
                        + " AND m.health = :health AND m.height = :height");
        jpql.append(m.getHeartCount() == null ? " AND m.heartCount IS NULL" : " AND m.heartCount = :heartCount");
        jpql.append(m.getMeleeWeapon() == null ? " AND m.meleeWeapon IS NULL" : " AND m.meleeWeapon = :meleeWeapon");
        if (excludeId != null) jpql.append(" AND m.id <> :exclude");
        jpql.append(" ORDER BY m.id");

        TypedQuery<SpaceMarine> q = em.createQuery(jpql.toString(), SpaceMarine.class)
                .setParameter("nameLength", nameLength)
                .setParameter("coordinatesId", m.getCoordinates().getId())
                .setParameter("chapterId", m.getChapter().getId())
                .setParameter("health", m.getHealth())
                .setParameter("height", m.getHeight());
        if (m.getHeartCount() != null) q.setParameter("heartCount", m.getHeartCount());
        if (m.getMeleeWeapon() != null) q.setParameter("meleeWeapon", m.getMeleeWeapon());
        if (excludeId != null) q.setParameter("exclude", excludeId);
        return q.getResultList();
    }

    /** Все десантники вместе с орденом и координатами — для карты. */
    public List<SpaceMarine> findAll() {
        return em.createQuery(
                "SELECT m FROM SpaceMarine m JOIN FETCH m.chapter JOIN FETCH m.coordinates ORDER BY m.id",
                SpaceMarine.class).getResultList();
    }

    /** Страница таблицы с фильтром (полное совпадение) и сортировкой. */
    public List<SpaceMarine> findPage(SpaceMarineFilter f) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<SpaceMarine> q = cb.createQuery(SpaceMarine.class);
        Root<SpaceMarine> root = q.from(SpaceMarine.class);
        Join<Object, Object> chapter = root.join("chapter");
        Join<Object, Object> owner = chapter.join("owner");
        Join<Object, Object> coords = root.join("coordinates");

        q.select(root).where(buildPredicates(cb, root, chapter, owner, f));

        Expression<?> sortExpr = sortExpression(f.sort, root, chapter, owner, coords);
        Order primary = "desc".equalsIgnoreCase(f.order) ? cb.desc(sortExpr) : cb.asc(sortExpr);
        q.orderBy(primary, cb.asc(root.get("id"))); // стабильный порядок между страницами

        return em.createQuery(q)
                .setFirstResult(f.page * f.size)
                .setMaxResults(f.size)
                .getResultList();
    }

    public long count(SpaceMarineFilter f) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<SpaceMarine> root = q.from(SpaceMarine.class);
        Join<Object, Object> chapter = root.join("chapter");
        Join<Object, Object> owner = chapter.join("owner");
        q.select(cb.count(root)).where(buildPredicates(cb, root, chapter, owner, f));
        return em.createQuery(q).getSingleResult();
    }

    private Predicate[] buildPredicates(CriteriaBuilder cb, Root<SpaceMarine> root,
                                        Join<Object, Object> chapter, Join<Object, Object> owner,
                                        SpaceMarineFilter f) {
        List<Predicate> p = new ArrayList<>();
        if (notEmpty(f.name)) {
            p.add(cb.equal(root.get("name"), f.name));
        }
        if (notEmpty(f.chapterName)) {
            p.add(cb.equal(chapter.get("name"), f.chapterName));
        }
        if (notEmpty(f.meleeWeapon)) {
            p.add(cb.equal(root.get("meleeWeapon"), MeleeWeapon.valueOf(f.meleeWeapon)));
        }
        if (notEmpty(f.ownerLogin)) {
            p.add(cb.equal(owner.get("login"), f.ownerLogin));
        }
        return p.toArray(new Predicate[0]);
    }

    private Expression<?> sortExpression(String sort, Root<SpaceMarine> root,
                                         Join<Object, Object> chapter, Join<Object, Object> owner,
                                         Join<Object, Object> coords) {
        return switch (sort == null ? "id" : sort) {
            case "name" -> root.get("name");
            case "creationDate" -> root.get("creationDate");
            case "health" -> root.get("health");
            case "heartCount" -> root.get("heartCount");
            case "height" -> root.get("height");
            case "meleeWeapon" -> root.get("meleeWeapon");
            case "chapterName" -> chapter.get("name");
            case "ownerLogin" -> owner.get("login");
            case "coordinatesX" -> coords.get("x");
            case "coordinatesY" -> coords.get("y");
            default -> root.get("id");
        };
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
