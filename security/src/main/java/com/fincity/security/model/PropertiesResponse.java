package com.fincity.security.model;

import java.io.Serializable;
import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.security.dto.AppProperty;
import com.fincity.security.dto.Client;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PropertiesResponse implements Serializable {

    private static final long serialVersionUID = 0x1232l;

    private Map<ULong, Map<String, AppProperty>> properties;
    private Map<ULong, Client> clients;
}
