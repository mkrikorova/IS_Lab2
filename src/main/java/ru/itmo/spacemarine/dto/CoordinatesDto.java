package ru.itmo.spacemarine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class CoordinatesDto {
    public Integer id;

    @NotNull(message = "x обязателен")
    public Long x;

    @NotNull(message = "y обязателен")
    @DecimalMin(value = "-833", inclusive = false, message = "y должен быть больше -833")
    public Double y;
}
