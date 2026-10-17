package ru.itmo.spacemarine.controller.mapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import ru.itmo.spacemarine.dto.ErrorDto;
import ru.itmo.spacemarine.service.exception.*;
import ru.itmo.spacemarine.tx.IsolationSupport;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Единые правила «исключение -> ErrorDto со статусом».
 * Используются и JAX-RS (GlobalExceptionMapper), и сервлетом загрузки файла,
 * поэтому не зависят от Response.
 * Исключения из @Transactional/EclipseLink приходят обёрнутыми — идём по цепочке cause.
 */
public final class ErrorMapping {

    private static final Logger LOG = Logger.getLogger(ErrorMapping.class.getName());

    private ErrorMapping() {
    }

    public static ErrorDto describe(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof WebApplicationException w) {
                int status = w.getResponse().getStatus();
                return new ErrorDto(status, w.getResponse().getStatusInfo().getReasonPhrase(), null);
            }
            if (t instanceof ConstraintViolationException cve) {
                return validation(cve);
            }
            if (t instanceof ImportException ie) {
                return new ErrorDto(ie.getStatus(), ie.getMessage(), ie.getDetails());
            }
            if (t instanceof NotAuthenticatedException) {
                return new ErrorDto(401, t.getMessage(), null);
            }
            if (t instanceof AccessDeniedException) {
                return new ErrorDto(403, t.getMessage(), null);
            }
            if (t instanceof ConflictException c) {
                // ошибка конкретного поля: общий заголовок + подробность под полем формы
                return c.getField() == null
                        ? new ErrorDto(409, c.getMessage(), null)
                        : new ErrorDto(409, "Нарушено ограничение уникальности",
                                List.of(c.getField() + ": " + c.getMessage()));
            }
            if (t instanceof NotFoundException) {
                return new ErrorDto(404, t.getMessage(), null);
            }
            if (t instanceof BadRequestException) {
                return new ErrorDto(400, t.getMessage(), null);
            }
            if (t instanceof LinkedObjectsException l) {
                return new ErrorDto(409, l.getMessage(), List.of("linkedCount: " + l.getLinkedCount()));
            }
            if (t.getClass().getSimpleName().contains("OptimisticLock")) {
                // @Version: строку между чтением и записью изменили или удалили в другой транзакции
                return new ErrorDto(409, "Объект изменён или удалён другим пользователем. "
                        + "Обновите данные и повторите действие", null);
            }
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return fromSql(sql);
            }
        }
        if (IsolationSupport.isRetryable(e)) {
            // транзакцию отменили из-за параллельной (в т.ч. при коммите) и повторы не помогли
            LOG.log(Level.WARNING, "Конфликт одновременных изменений: " + IsolationSupport.describe(e), e);
            return new ErrorDto(409, "Конфликт одновременных изменений: операцию не удалось выполнить. "
                    + "Повторите её", null);
        }
        LOG.log(Level.SEVERE, "Unexpected error: " + IsolationSupport.describe(e), e);
        return new ErrorDto(500, "Внутренняя ошибка сервера", null);
    }

    /** Ошибки Bean Validation -> 400 со списком «поле: сообщение». */
    public static ErrorDto validation(ConstraintViolationException e) {
        List<String> details = e.getConstraintViolations().stream()
                .map(ErrorMapping::format)
                .sorted()
                .toList();
        return new ErrorDto(400, "Некорректные данные", details);
    }

    private static String format(ConstraintViolation<?> v) {
        // путь вида "create.request.health" -> "health"
        String path = v.getPropertyPath().toString();
        String[] parts = path.split("\\.");
        String field = parts.length > 2 ? String.join(".", Arrays.copyOfRange(parts, 2, parts.length)) : path;
        return field + ": " + v.getMessage();
    }

    /** Ошибки ограничений БД и RAISE EXCEPTION из функций. */
    private static ErrorDto fromSql(SQLException e) {
        String state = e.getSQLState();
        String msg = firstLine(e.getMessage());
        return switch (state) {
            case "23503" -> new ErrorDto(409, "Нарушена связь между объектами", List.of(msg));   // foreign key
            case "23505" -> new ErrorDto(409, "Объект уже существует", List.of(msg));            // unique
            case "23502", "23514", "22023", "22003", "P0001" ->
                    new ErrorDto(400, "Данные не прошли проверку в БД", List.of(msg));        // not null / check / raise
            case "P0002" -> new ErrorDto(404, msg, null);                                        // raise ... 'P0002'
            case "40001", "40P01" -> new ErrorDto(409, "Конфликт одновременных изменений: операцию не удалось "
                    + "выполнить даже после повторов. Повторите её", List.of(msg));            // serialization / deadlock
            default -> {
                LOG.log(Level.SEVERE, "SQL error " + state, e);
                yield new ErrorDto(500, "Ошибка базы данных", List.of(msg));
            }
        };
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        String line = s.split("\\R", 2)[0];
        return line.startsWith("ERROR: ") ? line.substring(7) : line;
    }
}
