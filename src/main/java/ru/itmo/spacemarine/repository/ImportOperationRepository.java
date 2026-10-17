package ru.itmo.spacemarine.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import ru.itmo.spacemarine.model.ImportOperation;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ImportOperationRepository {

    @PersistenceContext(unitName = "spacemarinePU")
    private EntityManager em;

    public ImportOperation save(ImportOperation op) {
        em.persist(op);
        em.flush(); // сразу получить id
        return op;
    }

    public Optional<ImportOperation> findById(int id) {
        return Optional.ofNullable(em.find(ImportOperation.class, id));
    }

    /** userId == null — все операции (для администратора). Новые сверху. */
    public List<ImportOperation> findPage(Integer userId, int page, int size) {
        String jpql = "SELECT o FROM ImportOperation o"
                + (userId != null ? " WHERE o.user.id = :uid" : "")
                + " ORDER BY o.id DESC";
        TypedQuery<ImportOperation> q = em.createQuery(jpql, ImportOperation.class);
        if (userId != null) q.setParameter("uid", userId);
        return q.setFirstResult(page * size).setMaxResults(size).getResultList();
    }

    public long count(Integer userId) {
        String jpql = "SELECT COUNT(o) FROM ImportOperation o" + (userId != null ? " WHERE o.user.id = :uid" : "");
        TypedQuery<Long> q = em.createQuery(jpql, Long.class);
        if (userId != null) q.setParameter("uid", userId);
        return q.getSingleResult();
    }
}
