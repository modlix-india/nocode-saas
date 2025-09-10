package com.modlix.saas.commons2.mongo.model;

import java.util.List;

import com.modlix.saas.commons2.model.dto.AbstractOverridableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class Transport extends AbstractOverridableDTO<Transport> {

    private static final long serialVersionUID = -5436810186809455453L;

    private String uniqueTransportCode;
    private List<TransportObject> objects;
    private String type;

    @Override
    public Transport applyOverride(Transport base) {
        return this;
    }

    @Override
    public Transport makeOverride(Transport base) {
        return this;
    }
}

