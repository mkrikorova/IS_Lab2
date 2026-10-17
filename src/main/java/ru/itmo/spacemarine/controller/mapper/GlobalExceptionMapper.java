package ru.itmo.spacemarine.controller.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import ru.itmo.spacemarine.dto.ErrorDto;

/** Превращает любые исключения в понятный JSON для фронтенда (правила — в ErrorMapping). */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable e) {
        return json(ErrorMapping.describe(e));
    }

    static Response json(ErrorDto error) {
        return Response.status(error.status)
                .type(MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }
}
