package ru.itmo.spacemarine.websocket;

import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

/**
 * Точка подключения клиентов: ws://<хост>:<порт>/<контекст>/ws/changes
 * Клиент ничего не отправляет — только слушает уведомления об изменениях.
 * Объект эндпоинта создаётся заново на каждое соединение, поэтому
 * сами соединения хранятся в общем SessionRegistry.
 */
@ServerEndpoint("/ws/changes")
public class ChangesEndpoint {

    @Inject
    private SessionRegistry registry;

    @OnOpen
    public void onOpen(Session session) {
        registry.add(session);
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        registry.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        registry.remove(session);
    }
}
