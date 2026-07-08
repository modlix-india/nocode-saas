package com.modlix.saas.adzump.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ValidationIssue implements Serializable {

    @Serial
    private static final long serialVersionUID = 2910564738291056475L;

    private String code;
    private Severity severity;

    /**
     * Human-oriented slot / field label (e.g. {@code "keywords"}). Retained for the P0 wire
     * contract; {@link #path} is the machine-addressable pointer J6's repair loop keys on.
     */
    private String field;

    /**
     * JSON-pointer-ish address into the {@code CampaignPlan} (e.g. {@code "/body/adGroups/0/keywords"})
     * so A3 can repair the exact slot and the eval harness can assert on the location, not message text.
     * Added by J6 (extends the P0 issue shape).
     */
    private String path;

    private String message;

    public static ValidationIssue error(String code, String path, String field, String message) {
        return new ValidationIssue()
                .setCode(code)
                .setSeverity(Severity.ERROR)
                .setPath(path)
                .setField(field)
                .setMessage(message);
    }

    public static ValidationIssue warning(String code, String path, String field, String message) {
        return new ValidationIssue()
                .setCode(code)
                .setSeverity(Severity.WARNING)
                .setPath(path)
                .setField(field)
                .setMessage(message);
    }
}
