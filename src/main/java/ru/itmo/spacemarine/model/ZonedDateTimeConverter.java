package ru.itmo.spacemarine.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** JPA 3.x не поддерживает ZonedDateTime напрямую — храним как timestamptz. */
@Converter(autoApply = true)
public class ZonedDateTimeConverter implements AttributeConverter<ZonedDateTime, Timestamp> {

    @Override
    public Timestamp convertToDatabaseColumn(ZonedDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    @Override
    public ZonedDateTime convertToEntityAttribute(Timestamp value) {
        return value == null ? null : ZonedDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
    }
}
