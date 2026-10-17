package ru.itmo.spacemarine.service.exception;

/** Пользователь вошёл, но у него нет прав на действие -> 403. */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
