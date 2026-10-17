package ru.itmo.spacemarine.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ru.itmo.spacemarine.model.AppUser;

import java.util.Optional;

@ApplicationScoped
public class UserRepository {

    @PersistenceContext(unitName = "spacemarinePU")
    private EntityManager em;

    public Optional<AppUser> findByLogin(String login) {
        return em.createQuery("SELECT u FROM AppUser u WHERE u.login = :login", AppUser.class)
                .setParameter("login", login)
                .getResultStream()
                .findFirst();
    }

    public boolean existsByLogin(String login) {
        return em.createQuery("SELECT COUNT(u) FROM AppUser u WHERE u.login = :login", Long.class)
                .setParameter("login", login)
                .getSingleResult() > 0;
    }

    public AppUser save(AppUser user) {
        em.persist(user);
        em.flush();
        return user;
    }

    /** Управляемый объект пользователя — для владельца нового ордена. */
    public AppUser get(int id) {
        AppUser user = em.find(AppUser.class, id);
        if (user == null) {
            throw new IllegalStateException("Пользователь с id=" + id + " не найден");
        }
        return user;
    }
}
