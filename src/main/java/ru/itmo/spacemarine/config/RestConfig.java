package ru.itmo.spacemarine.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Все REST-контроллеры доступны по /api/... */
@ApplicationPath("/api")
public class RestConfig extends Application {
}
