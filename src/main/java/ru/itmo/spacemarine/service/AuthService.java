package ru.itmo.spacemarine.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.itmo.spacemarine.dto.AuthResponse;
import ru.itmo.spacemarine.dto.LoginRequest;
import ru.itmo.spacemarine.dto.RegisterRequest;
import ru.itmo.spacemarine.dto.UserDto;
import ru.itmo.spacemarine.model.AppUser;
import ru.itmo.spacemarine.model.AuthSession;
import ru.itmo.spacemarine.model.Role;
import ru.itmo.spacemarine.repository.AuthSessionRepository;
import ru.itmo.spacemarine.repository.UserRepository;
import ru.itmo.spacemarine.security.PasswordHasher;
import ru.itmo.spacemarine.security.Tokens;
import ru.itmo.spacemarine.security.UserPrincipal;
import ru.itmo.spacemarine.service.exception.ConflictException;
import ru.itmo.spacemarine.service.exception.NotAuthenticatedException;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

/** Регистрация, вход, выход и проверка токена. */
@ApplicationScoped
@Transactional
public class AuthService {

    /** Сколько живёт сессия без повторного входа. */
    private static final Duration SESSION_TTL = Duration.ofHours(12);

    @Inject
    private UserRepository users;
    @Inject
    private AuthSessionRepository sessions;

    /** Новый пользователь всегда получает роль USER; администраторы заводятся в schema.sql. */
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByLogin(req.login)) {
            throw new ConflictException("Логин «" + req.login + "» уже занят");
        }
        // при одновременной регистрации одного логина второй запрос упадёт
        // на UNIQUE(login) в БД -> 23505 -> 409 (см. GlobalExceptionMapper)
        AppUser user = users.save(new AppUser(req.login, PasswordHasher.hash(req.password.toCharArray()), Role.USER));
        return openSession(user);
    }

    public AuthResponse login(LoginRequest req) {
        Optional<AppUser> user = users.findByLogin(req.login);
        if (user.isEmpty()) {
            PasswordHasher.verifyDummy(req.password.toCharArray());
            throw new NotAuthenticatedException("Неверный логин или пароль");
        }
        if (!PasswordHasher.verify(req.password.toCharArray(), user.get().getPasswordHash())) {
            throw new NotAuthenticatedException("Неверный логин или пароль");
        }
        sessions.deleteExpired(ZonedDateTime.now()); // заодно чистим старые сессии
        return openSession(user.get());
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.delete(Tokens.sha256Hex(token));
        }
    }

    /** Пользователь по токену из заголовка; пусто, если токена нет или он истёк. */
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<UserPrincipal> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessions.findActive(Tokens.sha256Hex(token), ZonedDateTime.now())
                .map(s -> toPrincipal(s.getUser()));
    }

    private AuthResponse openSession(AppUser user) {
        String token = Tokens.newToken();
        ZonedDateTime now = ZonedDateTime.now();
        sessions.save(new AuthSession(Tokens.sha256Hex(token), user, now, now.plus(SESSION_TTL)));
        return new AuthResponse(token, toDto(user));
    }

    private static UserPrincipal toPrincipal(AppUser u) {
        return new UserPrincipal(u.getId(), u.getLogin(), u.getRole());
    }

    public static UserDto toDto(AppUser u) {
        return new UserDto(u.getId(), u.getLogin(), u.getRole());
    }

    public static UserDto toDto(UserPrincipal p) {
        return new UserDto(p.id(), p.login(), p.role());
    }
}
