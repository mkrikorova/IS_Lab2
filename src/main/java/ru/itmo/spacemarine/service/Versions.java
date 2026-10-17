package ru.itmo.spacemarine.service;

import ru.itmo.spacemarine.service.exception.BadRequestException;
import ru.itmo.spacemarine.service.exception.ConflictException;

/**
 * Проверка версии при изменении (оптимистическая блокировка «между запросами»).
 * Клиент читает объект в одном HTTP-запросе, а сохраняет — в другом, то есть в другой
 * транзакции. Никакой уровень изоляции не защитит от того, что между ними объект изменил
 * кто-то ещё; защищает только сравнение версии, которую видел клиент, с текущей.
 * Гонку внутри одной транзакции (между этой проверкой и UPDATE) закрывает @Version:
 * UPDATE ... WHERE version = ? не найдёт строку -> OptimisticLockException.
 */
public final class Versions {

    private Versions() {
    }

    public static void require(Long clientVersion, long currentVersion, String what) {
        if (clientVersion == null) {
            throw new BadRequestException("Не передана версия объекта (version) — без неё нельзя проверить, "
                    + "не изменил ли " + what + " другой пользователь");
        }
        if (clientVersion != currentVersion) {
            throw new ConflictException(capitalize(what) + " уже изменил другой пользователь (ваша версия "
                    + clientVersion + ", текущая " + currentVersion + "). Обновите данные и повторите изменение");
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
