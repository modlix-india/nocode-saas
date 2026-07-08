package com.modlix.saas.adzump.model.leadzump;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Adzump's view of a leadzump (entity-processor) product. Ids are the CRM's
 * string ids, not adzump ULongs.
 */
@Data
@Accessors(chain = true)
public class Product implements Serializable {

    @Serial
    private static final long serialVersionUID = 1543098765412387611L;

    private String id;
    private String clientCode;
    private String name;
    private String templateId;
    private String siteUrl;
    private String brand;
    private Map<String, Object> attributes;
}
