package com.fincity.saas.entity.processor.model.discovery;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class DiscoveredAdset implements Serializable {

    @Serial
    private static final long serialVersionUID = -4837112940561378250L;

    private String adsetId;
    private String adsetName;
    private String campaignId;
    private String status;
}
