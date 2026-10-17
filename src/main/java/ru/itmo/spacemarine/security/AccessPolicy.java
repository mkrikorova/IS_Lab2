package ru.itmo.spacemarine.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.itmo.spacemarine.model.Chapter;
import ru.itmo.spacemarine.model.SpaceMarine;
import ru.itmo.spacemarine.service.exception.AccessDeniedException;
import ru.itmo.spacemarine.service.exception.NotAuthenticatedException;

/**
 * Правила доступа («командующие и главнокомандующие»):
 * <ul>
 *   <li>смотреть данные может кто угодно, даже без входа;</li>
 *   <li>орденом управляет его владелец (командующий) или ADMIN;</li>
 *   <li>владелец десантника — владелец его ордена: записать десантника
 *       можно только в свой орден (ADMIN — в любой);</li>
 *   <li>координаты общие: создаёт любой вошедший, меняет и удаляет только ADMIN.</li>
 * </ul>
 * Все проверки выполняются на сервере; скрытые кнопки во фронтенде — только удобство.
 */
@ApplicationScoped
public class AccessPolicy {

    @Inject
    private CurrentUser currentUser;

    /** Любое изменение данных требует входа. */
    public UserPrincipal requireUser() {
        return currentUser.get().orElseThrow(
                () -> new NotAuthenticatedException("Войдите в систему, чтобы изменять данные"));
    }

    public UserPrincipal requireAdmin(String action) {
        UserPrincipal u = requireUser();
        if (!u.isAdmin()) {
            throw new AccessDeniedException(action + " может только администратор");
        }
        return u;
    }

    public boolean canManage(Chapter chapter) {
        return currentUser.get()
                .map(u -> u.isAdmin() || chapter.getOwner().getId() == u.id())
                .orElse(false);
    }

    /** Изменить/удалить/распустить орден. */
    public void requireManage(Chapter chapter) {
        requireUser();
        if (!canManage(chapter)) {
            throw new AccessDeniedException("Орден «" + chapter.getName() + "» принадлежит командующему "
                    + chapter.getOwner().getLogin() + ". Изменять его может только он или администратор");
        }
    }

    /** Записать десантника в орден (при создании, изменении, перепривязке). */
    public void requireAssignTo(Chapter chapter) {
        requireUser();
        if (!canManage(chapter)) {
            throw new AccessDeniedException("Нельзя записать десантника в чужой орден «" + chapter.getName()
                    + "» (командующий — " + chapter.getOwner().getLogin() + ")");
        }
    }

    /** Изменить/удалить десантника: он принадлежит владельцу своего ордена. */
    public void requireManage(SpaceMarine marine) {
        requireUser();
        if (!canManage(marine.getChapter())) {
            throw new AccessDeniedException("Десантник номер " + marine.getId() + " подчиняется командующему "
                    + marine.getChapter().getOwner().getLogin() + ". Изменять его может только он или администратор");
        }
    }
}
