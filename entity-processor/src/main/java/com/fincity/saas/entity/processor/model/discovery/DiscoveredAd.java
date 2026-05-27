package com.fincity.saas.entity.processor.model.discovery;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class DiscoveredAd implements Serializable {

    @Serial
    private static final long serialVersionUID = -2945101873625019472L;

    private String adId;
    private String adName;
    private String adsetId;
    private String campaignId;
    private String status;
    private String thumbnailUrl;
    private String creativeType;
}
