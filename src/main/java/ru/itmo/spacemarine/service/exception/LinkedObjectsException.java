package ru.itmo.spacemarine.service.exception;

/**
 * Удаляемый объект (орден / координаты) связан с десантниками,
 * а объект для перепривязки не выбран. Фронтенд должен предложить выбрать его.
 */
public class LinkedObjectsException extends RuntimeException {
    private final long linkedCount;

    public LinkedObjectsException(String message, long linkedCount) {
        super(message);
        this.linkedCount = linkedCount;
    }

    public long getLinkedCount() {
        return linkedCount;
    }
}
