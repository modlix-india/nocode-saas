package com.modlix.saas.adzump.model.leadzump;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The adzump-defined CRM outcomes read (J11 §5.1): join outcomes by ad-grain
 * ID, per product template, timezone + date-range parameterized. Backed by a
 * NEW lean entity-processor endpoint (P2) - NOT the existing
 * analytics/campaigns/tree.
 *
 * NOTE: clientCode here is transport only - the service layer always sets it
 * from ContextAuthentication, never trusts it from a request body.
 */
@Data
@Accessors(chain = true)
public class OutcomeQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1543098765412387616L;

    private String clientCode;
    private String productTemplateId;
    private List<AdGrainId> ids;
    private String timezone;
    private LocalDate from;
    private LocalDate to;
    private Grain grain;
}
