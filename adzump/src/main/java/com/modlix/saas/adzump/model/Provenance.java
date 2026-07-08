package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.modlix.saas.adzump.enums.ProvenanceSource;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Provenance implements Serializable {

    @Serial
    private static final long serialVersionUID = 1056473829105647383L;

    private ProvenanceSource source;
    private String rationale;
    private Double confidence;
    private LocalDateTime at;
}
