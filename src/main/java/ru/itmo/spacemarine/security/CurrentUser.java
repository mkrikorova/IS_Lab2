package ru.itmo.spacemarine.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import ru.itmo.spacemarine.service.AuthService;

import java.util.Optional;

/**
 * Пользователь текущего HTTP-запроса. Токен берётся из заголовка
 * «Authorization: Bearer <token>» и проверяется лениво — только если
 * бизнес-логике действительно нужно знать, кто выполняет запрос.
 * Просмотр данных (GET) доступен без входа, поэтому отсутствие токена — не ошибка.
 */
@RequestScoped
public class CurrentUser {

    private static final String BEARER = "Bearer ";

    @Inject
    private HttpServletRequest request;
    @Inject
    private AuthService auth;

    private boolean resolved;
    private UserPrincipal user;

    public Optional<UserPrincipal> get() {
        if (!resolved) {
            user = auth.resolve(token()).orElse(null);
            resolved = true;
        }
        return Optional.ofNullable(user);
    }

    /** Токен из заголовка или null. */
    public String token() {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        return header.substring(BEARER.length()).trim();
    }
}
