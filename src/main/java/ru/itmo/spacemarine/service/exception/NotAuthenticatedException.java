package ru.itmo.spacemarine.service.exception;

/** Действие требует входа в систему -> 401. */
public class NotAuthenticatedException extends RuntimeException {
    public NotAuthenticatedException(String message) {
        super(message);
    }
}
