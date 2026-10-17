package ru.itmo.spacemarine.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Хэширование паролей PBKDF2-HMAC-SHA256 средствами JDK (без сторонних библиотек).
 * Формат строки: pbkdf2_sha256$<итерации>$<соль base64>$<хэш base64>.
 * Число итераций хранится вместе с хэшем, поэтому его можно увеличить,
 * не ломая уже сохранённые пароли.
 */
public final class PasswordHasher {

    private static final String PREFIX = "pbkdf2_sha256";
    private static final int ITERATIONS = 100_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Хэш несуществующего пароля: сверяемся с ним, если логин не найден (одинаковое время ответа). */
    private static final String DUMMY = hash("dummy-password".toCharArray());

    private PasswordHasher() {
    }

    public static String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(key);
    }

    public static boolean verify(char[] password, String stored) {
        if (stored == null) {
            stored = DUMMY;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations);
            // сравнение за постоянное время — не подсказываем, сколько байт совпало
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Сверка с фиктивным хэшем — чтобы вход с неизвестным логином занимал столько же времени. */
    public static void verifyDummy(char[] password) {
        verify(password, DUMMY);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 недоступен", e);
        }
    }
}
