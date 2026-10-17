package ru.itmo.spacemarine.dto;

/** Ответ на вход/регистрацию: токен передаётся дальше в заголовке Authorization: Bearer <token>. */
public class AuthResponse {
    public String token;
    public UserDto user;

    public AuthResponse() {
    }

    public AuthResponse(String token, UserDto user) {
        this.token = token;
        this.user = user;
    }
}
