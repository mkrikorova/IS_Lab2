package ru.itmo.spacemarine.security;

import ru.itmo.spacemarine.model.Role;

/**
 * Кто выполняет запрос. Неизменяемый снимок пользователя (не JPA-entity),
 * чтобы его можно было безопасно передавать между транзакциями.
 */
public record UserPrincipal(int id, String login, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
