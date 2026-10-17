package ru.itmo.spacemarine.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Регистрация нового командующего (роль USER). Ограничения логина совпадают с CHECK в БД. */
public class RegisterRequest {
    @NotNull(message = "Введите логин")
    @Pattern(regexp = "[A-Za-z0-9_.-]{3,50}",
            message = "Логин: от 3 до 50 символов — латинские буквы, цифры, точка, дефис, подчёркивание")
    public String login;

    @NotNull(message = "Введите пароль")
    @Size(min = 4, max = 100, message = "Пароль: от 4 до 100 символов")
    public String password;
}
