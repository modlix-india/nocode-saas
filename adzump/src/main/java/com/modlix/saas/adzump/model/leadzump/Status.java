package com.modlix.saas.adzump.model.leadzump;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One ticket status of a product template's pipeline.
 */
@Data
@Accessors(chain = true)
public class Status implements Serializable {

    @Serial
    private static final long serialVersionUID = 1543098765412387613L;

    private String key;
    private String name;
}
