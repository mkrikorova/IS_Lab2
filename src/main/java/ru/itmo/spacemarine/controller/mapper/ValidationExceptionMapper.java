package ru.itmo.spacemarine.controller.mapper;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Ошибки Bean Validation -> 400 со списком «поле: сообщение». */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException e) {
        return GlobalExceptionMapper.json(ErrorMapping.validation(e));
    }
}
