package ru.itmo.spacemarine.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Список всех открытых WebSocket-соединений (по одному на вкладку браузера).
 * Один объект на всё приложение (@ApplicationScoped), потокобезопасный.
 */
@ApplicationScoped
public class SessionRegistry {

    private static final Logger LOG = Logger.getLogger(SessionRegistry.class.getName());

    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    public void add(Session session) {
        sessions.add(session);
    }

    public void remove(Session session) {
        sessions.remove(session);
    }

    public int size() {
        return sessions.size();
    }

    /** Отправить текст всем подключённым клиентам. */
    public void broadcast(String message) {
        for (Session s : sessions) {
            if (!s.isOpen()) {
                sessions.remove(s);
                continue;
            }
            // одновременно в одну сессию писать нельзя — синхронизируемся по ней
            synchronized (s) {
                try {
                    s.getBasicRemote().sendText(message);
                } catch (IOException | IllegalStateException e) {
                    LOG.log(Level.FINE, "Не удалось отправить в сессию " + s.getId(), e);
                    sessions.remove(s);
                }
            }
        }
    }
}
