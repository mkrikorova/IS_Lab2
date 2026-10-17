package ru.itmo.spacemarine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.itmo.spacemarine.model.MeleeWeapon;

/**
 * Запрос на создание/изменение десантника.
 * Для координат и ордена передаётся ЛИБО id существующего объекта (coordinatesId / chapterId),
 * ЛИБО новый объект (coordinates / chapter) — ровно одно из двух.
 * id и creationDate клиент не передаёт: их генерирует сервер/БД.
 */
public class SpaceMarineRequest {

    @NotNull(message = "Имя обязательно")
    @NotBlank(message = "Имя не может быть пустым")
    public String name;

    public Integer coordinatesId;
    @Valid
    public CoordinatesDto coordinates;

    public Integer chapterId;
    @Valid
    public ChapterDto chapter;

    @NotNull(message = "health обязательно")
    @Positive(message = "health должно быть больше 0")
    public Long health;

    @Positive(message = "heartCount должно быть больше 0")
    @Max(value = 3, message = "heartCount не может быть больше 3")
    public Integer heartCount;

    @NotNull(message = "height обязателен")
    public Integer height;

    public MeleeWeapon meleeWeapon;
}
