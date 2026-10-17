package ru.itmo.spacemarine.dto;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

/**
 * Параметры таблицы: пагинация, фильтр по ПОЛНОМУ совпадению строковых колонок, сортировка.
 * Пример: /api/space-marines?page=0&size=10&chapterName=Ultramarines&sort=name&order=asc
 */
public class SpaceMarineFilter {
    @QueryParam("page") @DefaultValue("0")
    public int page;

    @QueryParam("size") @DefaultValue("10")
    public int size;

    @QueryParam("name")
    public String name;

    @QueryParam("chapterName")
    public String chapterName;

    @QueryParam("meleeWeapon")
    public String meleeWeapon;

    /** Логин командующего (владельца ордена) — тоже строковая колонка таблицы. */
    @QueryParam("ownerLogin")
    public String ownerLogin;

    /** id, name, creationDate, health, heartCount, height, meleeWeapon, chapterName, ownerLogin, coordinatesX, coordinatesY */
    @QueryParam("sort") @DefaultValue("id")
    public String sort;

    @QueryParam("order") @DefaultValue("asc")
    public String order;
}
