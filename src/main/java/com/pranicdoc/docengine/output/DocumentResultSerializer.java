package com.pranicdoc.docengine.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.UncheckedIOException;

public class DocumentResultSerializer {

    private final ObjectMapper mapper;

    public DocumentResultSerializer() {
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String toJson(DocumentResult result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize DocumentResult", e);
        }
    }
}
