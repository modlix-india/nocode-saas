package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Placements implements Serializable {

    @Serial
    private static final long serialVersionUID = 8475610293847561029L;

    private Boolean automatic;
    private List<String> manual;
}
