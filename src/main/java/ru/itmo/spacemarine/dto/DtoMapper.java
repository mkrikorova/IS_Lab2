package ru.itmo.spacemarine.dto;

import ru.itmo.spacemarine.model.AppUser;
import ru.itmo.spacemarine.model.Chapter;
import ru.itmo.spacemarine.model.Coordinates;
import ru.itmo.spacemarine.model.SpaceMarine;

/** Преобразования entity <-> dto. Entity наружу не отдаём. */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static ChapterDto toDto(Chapter c) {
        if (c == null) return null;
        ChapterDto d = new ChapterDto();
        d.id = c.getId();
        d.name = c.getName();
        d.marinesCount = c.getMarinesCount();
        if (c.getOwner() != null) {
            d.ownerId = c.getOwner().getId();
            d.ownerLogin = c.getOwner().getLogin();
        }
        return d;
    }

    public static CoordinatesDto toDto(Coordinates c) {
        if (c == null) return null;
        CoordinatesDto d = new CoordinatesDto();
        d.id = c.getId();
        d.x = c.getX();
        d.y = c.getY();
        return d;
    }

    public static SpaceMarineDto toDto(SpaceMarine m) {
        SpaceMarineDto d = new SpaceMarineDto();
        d.id = m.getId();
        d.name = m.getName();
        d.coordinates = toDto(m.getCoordinates());
        d.creationDate = m.getCreationDate();
        d.chapter = toDto(m.getChapter());
        d.health = m.getHealth();
        d.heartCount = m.getHeartCount();
        d.height = m.getHeight();
        d.meleeWeapon = m.getMeleeWeapon();
        return d;
    }

    public static Chapter toEntity(ChapterDto d, AppUser owner) {
        return new Chapter(d.name, d.marinesCount, owner);
    }

    public static Coordinates toEntity(CoordinatesDto d) {
        return new Coordinates(d.x, d.y);
    }
}
