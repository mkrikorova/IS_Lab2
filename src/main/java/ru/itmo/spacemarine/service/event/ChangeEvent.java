package ru.itmo.spacemarine.service.event;

/**
 * CDI-событие об изменении данных. Сервисы отправляют его внутри транзакции,
 * websocket.ChangeBroadcaster получает его после коммита и рассылает всем клиентам.
 * id == null означает «изменилось несколько объектов сразу».
 */
public record ChangeEvent(String entity, Action action, Integer id) {
    public enum Action { CREATED, UPDATED, DELETED }
}
