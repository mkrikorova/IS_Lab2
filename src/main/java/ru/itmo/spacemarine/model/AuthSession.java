package ru.itmo.spacemarine.model;

import jakarta.persistence.*;

import java.time.ZonedDateTime;

/**
 * Сессия входа. Клиенту отдаётся случайный токен, в БД хранится только его SHA-256:
 * даже прочитав таблицу, нельзя войти под чужим пользователем.
 */
@Entity
@Table(name = "auth_session")
public class AuthSession {

    @Id
    @Column(name = "token_hash", length = 64, nullable = false, updatable = false)
    private String tokenHash;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    public AuthSession() {
    }

    public AuthSession(String tokenHash, AppUser user, ZonedDateTime createdAt, ZonedDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public AppUser getUser() { return user; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getExpiresAt() { return expiresAt; }
}
