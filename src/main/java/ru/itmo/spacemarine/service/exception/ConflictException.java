package ru.itmo.spacemarine.service.exception;

/**
 * Конфликт с текущим состоянием данных (занятый логин, нарушение уникальности) -> 409.
 * field — поле формы, к которому относится ошибка (например, "name" или "chapter.name"):
 * фронтенд покажет сообщение прямо под этим полем. null — ошибка всей формы.
 */
public class ConflictException extends RuntimeException {
    private final String field;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
