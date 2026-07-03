package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Geo implements Serializable {

    @Serial
    private static final long serialVersionUID = -5647382910564738291L;

    private String type;
    private Center center;
    private Double radiusKm;
    private List<String> places;
    private List<String> excluded;

    @Data
    @Accessors(chain = true)
    public static class Center implements Serializable {

        @Serial
        private static final long serialVersionUID = 1029384756102938475L;

        private Double lat;
        private Double lng;
    }
}
