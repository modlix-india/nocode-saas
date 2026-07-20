package com.modlix.saas.adzump.model.leadzump;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * A platform ad-grain identifier (campaign / adset / ad ids as the platforms
 * issue them). Which parts are set depends on the Grain of the read.
 */
@Data
@Accessors(chain = true)
public class AdGrainId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1543098765412387615L;

    private String campaignId;
    private String adSetId;
    private String adId;
}
