package ru.itmo.spacemarine.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank(message = "Введите логин")
    public String login;

    @NotBlank(message = "Введите пароль")
    public String password;
}
