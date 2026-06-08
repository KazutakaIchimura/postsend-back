package com.example.sendmail.config;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class YearMonthSerializer extends ValueSerializer<LocalDate> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public void serialize(LocalDate value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        gen.writeString(value.format(FORMATTER));
    }
}
