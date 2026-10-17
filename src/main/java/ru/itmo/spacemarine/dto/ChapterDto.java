package ru.itmo.spacemarine.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Орден: в ответах id и владелец заполнены, в запросах на создание/изменение игнорируются
 * (владельцем нового ордена всегда становится текущий пользователь).
 */
public class ChapterDto {
    public Integer id;

    /** Командующий ордена — только в ответах. */
    public Integer ownerId;
    public String ownerLogin;

    @NotNull(message = "Имя ордена обязательно")
    @NotBlank(message = "Имя ордена не может быть пустым")
    public String name;

    @NotNull(message = "marinesCount обязателен")
    @Positive(message = "marinesCount должен быть больше 0")
    @Max(value = 1000, message = "marinesCount не может быть больше 1000")
    public Long marinesCount;
}
