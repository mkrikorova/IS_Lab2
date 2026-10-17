package ru.itmo.spacemarine.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ru.itmo.spacemarine.model.SpaceMarine;

import java.util.List;

/** Вызовы функций БД из schema.sql. */
@ApplicationScoped
public class SpecialOperationsRepository {

    @PersistenceContext(unitName = "spacemarinePU")
    private EntityManager em;

    public long sumHeartCount() {
        Number n = (Number) em.createNativeQuery("SELECT sm_sum_heart_count()").getSingleResult();
        return n.longValue();
    }

    /** Строки вида [melee_weapon (String или null), cnt (Long)]. */
    @SuppressWarnings("unchecked")
    public List<Object[]> groupByMeleeWeapon() {
        return em.createNativeQuery("SELECT melee_weapon, cnt FROM sm_group_by_melee_weapon()")
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<SpaceMarine> findByNameSubstring(String substring) {
        return em.createNativeQuery("SELECT * FROM sm_find_by_name_substring(?1)", SpaceMarine.class)
                .setParameter(1, substring)
                .getResultList();
    }

    public int createChapter(String name, long marinesCount, int ownerId) {
        Number id = (Number) em.createNativeQuery("SELECT sm_create_chapter(?1, ?2, ?3)")
                .setParameter(1, name)
                .setParameter(2, marinesCount)
                .setParameter(3, ownerId)
                .getSingleResult();
        return id.intValue();
    }

    public int disbandChapter(int chapterId) {
        Number deleted = (Number) em.createNativeQuery("SELECT sm_disband_chapter(?1)")
                .setParameter(1, chapterId)
                .getSingleResult();
        em.clear(); // функция удалила строки в обход контекста персистентности
        return deleted.intValue();
    }
}
