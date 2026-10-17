package ru.itmo.spacemarine.service.exception;

import java.util.List;

/**
 * Импорт не выполнен: файл не разобрался, записи не прошли проверку или
 * противоречат данным в БД. details — по строке на каждую проблему
 * («Запись 3: health — должно быть больше 0»), чтобы пользователь мог исправить файл.
 */
public class ImportException extends RuntimeException {
    private final int status;
    private final List<String> details;

    public ImportException(int status, String message, List<String> details) {
        super(message);
        this.status = status;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public int getStatus() {
        return status;
    }

    public List<String> getDetails() {
        return details;
    }
}
