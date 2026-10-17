package ru.itmo.spacemarine.dto;

import java.util.List;

/** Единый формат ошибки для фронтенда. */
public class ErrorDto {
    public int status;
    public String message;
    /** Ошибки по полям: "health: health должно быть больше 0" */
    public List<String> details;

    public ErrorDto() {
    }

    public ErrorDto(int status, String message, List<String> details) {
        this.status = status;
        this.message = message;
        this.details = details;
    }
}
