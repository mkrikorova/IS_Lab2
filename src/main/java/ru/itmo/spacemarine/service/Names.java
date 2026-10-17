package ru.itmo.spacemarine.service;

import java.util.Locale;

/**
 * Нормализация строк предметной области.
 * normalize: «  Ultra   Marines » -> «Ultra Marines» (так строка и сохраняется в БД);
 * key:       ключ сравнения без учёта регистра — «ultra marines».
 * Регистр приводится в Java (Locale.ROOT), а не функцией lower() в БД:
 * lower() в PostgreSQL зависит от локали базы (при LC_CTYPE=C кириллица
 * не приводится), а Java одинаково работает и с латиницей, и с кириллицей.
 */
public final class Names {

    private Names() {
    }

    public static String normalize(String s) {
        return s == null ? null : s.strip().replaceAll("\\s+", " ");
    }

    public static String key(String s) {
        String n = normalize(s);
        return n == null ? null : n.toLowerCase(Locale.ROOT);
    }

    /** Длина в символах (code points) — так же считает length() в PostgreSQL. */
    public static int length(String s) {
        return s.codePointCount(0, s.length());
    }
}
