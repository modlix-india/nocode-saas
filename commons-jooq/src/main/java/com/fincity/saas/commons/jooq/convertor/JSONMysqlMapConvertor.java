package com.fincity.saas.commons.jooq.convertor;

import java.io.IOException;
import java.io.Serial;
import java.util.Map;

import org.jooq.Converter;
import org.jooq.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("rawtypes")
public class JSONMysqlMapConvertor implements Converter<JSON, Map> {

    @Serial
    private static final long serialVersionUID = -2036360252040485619L;
    private static final Logger logger = LoggerFactory.getLogger(JSONMysqlMapConvertor.class);


    private final Class<JSON> fromClass;
    private final Class<Map> toClass;

    public JSONMysqlMapConvertor() {
        this(JSON.class, Map.class);
    }

    public JSONMysqlMapConvertor(Class<JSON> from, Class<Map> to) {
        this.fromClass = from;
        this.toClass = to;
    }

    @Override
    public Map from(JSON json) {

        if (json == null)
            return Map.of();

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(json.data(), new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            logger.error("Unable to convert {} to map of string and objects.", json.data(), e);
            return Map.of();
        }
    }

    @Override
    public JSON to(Map map) {

        if (map == null)
            return null;

        ObjectMapper mapper = new ObjectMapper();
        String value = null;
        try {
            value = mapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            logger.error("Unable to convert {} to json string.", map, e);
        }
        return JSON.jsonOrNull(value);
    }

    @Override
    public Class<JSON> fromType() {
        return this.fromClass;
    }

    @Override
    public Class<Map> toType() {
        return this.toClass;
    }
}
