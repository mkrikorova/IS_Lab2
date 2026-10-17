package ru.itmo.spacemarine.dto;

import ru.itmo.spacemarine.model.ImportStatus;

import java.time.ZonedDateTime;

/** Строка таблицы «История импорта». */
public class ImportOperationDto {
    public Integer id;
    public ImportStatus status;
    public Integer userId;
    public String userLogin;
    public String fileName;
    /** Число добавленных объектов — только для успешно завершённых операций. */
    public Integer addedCount;
    public Integer skippedCount;
    public String errorMessage;
    public ZonedDateTime startedAt;
    public ZonedDateTime finishedAt;
}
