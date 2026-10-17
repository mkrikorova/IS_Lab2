package ru.itmo.spacemarine.service.imports;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParsingException;
import ru.itmo.spacemarine.dto.ChapterDto;
import ru.itmo.spacemarine.dto.CoordinatesDto;
import ru.itmo.spacemarine.dto.SpaceMarineRequest;
import ru.itmo.spacemarine.model.MeleeWeapon;
import ru.itmo.spacemarine.service.exception.ImportException;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Разбор файла импорта (формат согласован: JSON-массив десантников,
 * поля вложенных объектов coordinates и chapter задаются в той же записи):
 * <pre>
 * [
 *   { "name": "Titus",
 *     "coordinates": { "x": 10, "y": 20.5 },
 *     "chapter": { "name": "Ultramarines", "marinesCount": 700 },
 *     "health": 100, "heartCount": 2, "height": 190, "meleeWeapon": "CHAIN_SWORD" }
 * ]
 * </pre>
 * Здесь проверяются СТРУКТУРА и ТИПЫ (JSON корректен, поля известны, числа — числа).
 * Ограничения предметной области (health > 0 и т.д.) проверяет Bean Validation
 * в ImportFacade — по тем же аннотациям, что и при ручном создании.
 * Ошибки не обрываются на первой: собираются по всем записям.
 */
public final class ImportParser {

    public static final int MAX_RECORDS = 10_000;
    private static final int MAX_ERRORS = 100;

    private static final Set<String> MARINE_FIELDS = Set.of(
            "name", "coordinates", "chapter", "health", "heartCount", "height", "meleeWeapon");
    private static final Set<String> COORDINATES_FIELDS = Set.of("x", "y");
    private static final Set<String> CHAPTER_FIELDS = Set.of("name", "marinesCount");

    private final List<String> errors = new ArrayList<>();

    private ImportParser() {
    }

    public static List<SpaceMarineRequest> parse(byte[] content) {
        return new ImportParser().run(content);
    }

    private List<SpaceMarineRequest> run(byte[] content) {
        if (content == null || content.length == 0) {
            throw fail("Файл пустой");
        }
        String text = decode(content); // ошибку кодировки сообщаем отдельно от ошибки JSON
        JsonValue root;
        try (JsonReader reader = Json.createReader(new StringReader(text))) {
            root = reader.readValue();
        } catch (JsonParsingException e) {
            JsonLocation l = e.getLocation();
            String where = l == null ? "" : " (строка " + l.getLineNumber() + ", позиция " + l.getColumnNumber() + ")";
            throw fail("Файл не является корректным JSON" + where);
        } catch (RuntimeException e) {
            throw fail("Файл не является корректным JSON");
        }
        if (root.getValueType() != JsonValue.ValueType.ARRAY) {
            throw fail("Ожидается JSON-массив десантников: [ {...}, {...} ]");
        }
        JsonArray array = root.asJsonArray();
        if (array.isEmpty()) {
            throw fail("В файле нет ни одной записи");
        }
        if (array.size() > MAX_RECORDS) {
            throw fail("Слишком много записей: " + array.size() + ", максимум " + MAX_RECORDS);
        }

        List<SpaceMarineRequest> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size() && errors.size() < MAX_ERRORS; i++) {
            String rec = "Запись " + (i + 1);
            JsonValue v = array.get(i);
            if (v.getValueType() != JsonValue.ValueType.OBJECT) {
                errors.add(rec + ": ожидается объект { ... }");
                continue;
            }
            result.add(marine(rec, v.asJsonObject()));
        }
        if (!errors.isEmpty()) {
            throw new ImportException(400, "Файл не прошёл проверку, ни один объект не создан", errors);
        }
        return result;
    }

    private SpaceMarineRequest marine(String rec, JsonObject o) {
        unknownFields(rec, "", o, MARINE_FIELDS);
        SpaceMarineRequest r = new SpaceMarineRequest();
        r.name = string(rec, "name", o, true);
        r.health = longValue(rec, "health", o, true);
        r.heartCount = intValue(rec, "heartCount", o, false);
        r.height = intValue(rec, "height", o, true);
        r.meleeWeapon = weapon(rec, o);

        JsonObject c = object(rec, "coordinates", o);
        if (c != null) {
            unknownFields(rec, "coordinates.", c, COORDINATES_FIELDS);
            r.coordinates = new CoordinatesDto();
            r.coordinates.x = longValue(rec, "coordinates.x", c, "x", true);
            r.coordinates.y = doubleValue(rec, "coordinates.y", c, "y");
        }
        JsonObject ch = object(rec, "chapter", o);
        if (ch != null) {
            unknownFields(rec, "chapter.", ch, CHAPTER_FIELDS);
            r.chapter = new ChapterDto();
            r.chapter.name = string(rec, "chapter.name", ch, "name", true);
            r.chapter.marinesCount = longValue(rec, "chapter.marinesCount", ch, "marinesCount", true);
        }
        return r;
    }

    // ---------- чтение полей с проверкой типа ----------

    private void unknownFields(String rec, String prefix, JsonObject o, Set<String> allowed) {
        Set<String> unknown = new LinkedHashSet<>(o.keySet());
        unknown.removeAll(allowed);
        for (String k : unknown) {
            errors.add(rec + ": неизвестное поле «" + prefix + k + "»"
                    + ("".equals(prefix) && (k.equals("id") || k.equals("creationDate"))
                    ? " — id и дата создания генерируются автоматически" : ""));
        }
    }

    /** null, если поля нет или оно null; ошибка, если обязательное. */
    private JsonValue value(String rec, String label, JsonObject o, String key, boolean required) {
        JsonValue v = o.get(key);
        if (v == null || v.getValueType() == JsonValue.ValueType.NULL) {
            if (required) errors.add(rec + ": " + label + " — обязательное поле");
            return null;
        }
        return v;
    }

    private String string(String rec, String key, JsonObject o, boolean required) {
        return string(rec, key, o, key, required);
    }

    private String string(String rec, String label, JsonObject o, String key, boolean required) {
        JsonValue v = value(rec, label, o, key, required);
        if (v == null) return null;
        if (v.getValueType() != JsonValue.ValueType.STRING) {
            errors.add(rec + ": " + label + " — ожидается строка");
            return null;
        }
        return ((JsonString) v).getString();
    }

    private JsonObject object(String rec, String key, JsonObject o) {
        JsonValue v = value(rec, key, o, key, true);
        if (v == null) return null;
        if (v.getValueType() != JsonValue.ValueType.OBJECT) {
            errors.add(rec + ": " + key + " — ожидается объект { ... }");
            return null;
        }
        return v.asJsonObject();
    }

    private Long longValue(String rec, String key, JsonObject o, boolean required) {
        return longValue(rec, key, o, key, required);
    }

    private Long longValue(String rec, String label, JsonObject o, String key, boolean required) {
        BigDecimal n = integral(rec, label, o, key, required);
        if (n == null) return null;
        try {
            return n.longValueExact();
        } catch (ArithmeticException e) {
            errors.add(rec + ": " + label + " — число вне допустимого диапазона");
            return null;
        }
    }

    private Integer intValue(String rec, String key, JsonObject o, boolean required) {
        BigDecimal n = integral(rec, key, o, key, required);
        if (n == null) return null;
        try {
            return n.intValueExact();
        } catch (ArithmeticException e) {
            errors.add(rec + ": " + key + " — число вне допустимого диапазона (int)");
            return null;
        }
    }

    private BigDecimal integral(String rec, String label, JsonObject o, String key, boolean required) {
        JsonValue v = value(rec, label, o, key, required);
        if (v == null) return null;
        if (v.getValueType() != JsonValue.ValueType.NUMBER) {
            errors.add(rec + ": " + label + " — ожидается целое число");
            return null;
        }
        BigDecimal n = ((JsonNumber) v).bigDecimalValue();
        if (n.signum() != 0 && n.stripTrailingZeros().scale() > 0) {
            errors.add(rec + ": " + label + " — ожидается целое число, получено " + n.toPlainString());
            return null;
        }
        return n;
    }

    private Double doubleValue(String rec, String label, JsonObject o, String key) {
        JsonValue v = value(rec, label, o, key, true);
        if (v == null) return null;
        if (v.getValueType() != JsonValue.ValueType.NUMBER) {
            errors.add(rec + ": " + label + " — ожидается число");
            return null;
        }
        double d = ((JsonNumber) v).doubleValue();
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            errors.add(rec + ": " + label + " — число вне допустимого диапазона");
            return null;
        }
        return d;
    }

    private MeleeWeapon weapon(String rec, JsonObject o) {
        String s = string(rec, "meleeWeapon", o, false);
        if (s == null) return null;
        try {
            return MeleeWeapon.valueOf(s);
        } catch (IllegalArgumentException e) {
            errors.add(rec + ": meleeWeapon — неизвестное значение «" + s + "», допустимо: "
                    + Arrays.toString(MeleeWeapon.values()) + " или null");
            return null;
        }
    }

    // ---------- служебное ----------

    /** Строгое UTF-8 (с BOM или без) — битая кодировка не превращается молча в «�». */
    private static String decode(byte[] content) {
        int offset = content.length >= 3 && (content[0] & 0xFF) == 0xEF
                && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content, offset, content.length - offset))
                    .toString();
        } catch (CharacterCodingException e) {
            throw fail("Файл должен быть в кодировке UTF-8");
        }
    }

    private static ImportException fail(String message) {
        return new ImportException(400, message + ". Ни один объект не создан", List.of());
    }
}
