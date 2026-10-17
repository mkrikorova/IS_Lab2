package ru.itmo.spacemarine.dto;

import ru.itmo.spacemarine.model.MeleeWeapon;

import java.time.ZonedDateTime;

/** Ответ: десантник вместе со связанными объектами. */
public class SpaceMarineDto {
    public Integer id;
    public String name;
    public CoordinatesDto coordinates;
    public ZonedDateTime creationDate;
    public ChapterDto chapter;
    public long health;
    public Integer heartCount;
    public int height;
    public MeleeWeapon meleeWeapon;
}
