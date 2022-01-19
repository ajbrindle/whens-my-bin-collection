package com.sk7software.bincollection.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;

public class JSONDateSerializer extends JsonSerializer<DateTime> {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private DateTimeFormatter formatter = DateTimeFormat.forPattern(DATE_FORMAT);

    public JSONDateSerializer() {
        super();
    }

    @Override
    public void serialize(DateTime dt, JsonGenerator jg, SerializerProvider sp)
            throws IOException, JsonProcessingException {
        jg.writeString(dt.toString(formatter));
    }
}
