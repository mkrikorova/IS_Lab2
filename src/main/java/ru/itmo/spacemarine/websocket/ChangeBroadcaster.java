package ru.itmo.spacemarine.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import ru.itmo.spacemarine.service.event.ChangeEvent;

/**
 * Мост между сервисами и WebSocket.
 * Сервисы отправляют ChangeEvent внутри транзакции; этот метод вызывается
 * ТОЛЬКО ПОСЛЕ успешного коммита (AFTER_SUCCESS). Если транзакция откатилась,
 * уведомление не уходит — клиенты не увидят «призрачных» изменений.
 */
@ApplicationScoped
public class ChangeBroadcaster {

    @Inject
    private SessionRegistry registry;

    public void onChange(@Observes(during = TransactionPhase.AFTER_SUCCESS) ChangeEvent event) {
        if (registry.size() == 0) {
            return;
        }
        // {"entity":"spaceMarine","action":"CREATED","id":5}
        JsonObjectBuilder json = Json.createObjectBuilder()
                .add("entity", event.entity())
                .add("action", event.action().name());
        if (event.id() != null) {
            json.add("id", event.id());
        } else {
            json.addNull("id");
        }
        registry.broadcast(json.build().toString());
    }
}
