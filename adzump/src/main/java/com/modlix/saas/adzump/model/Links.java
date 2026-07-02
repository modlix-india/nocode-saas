package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Links implements Serializable {

    @Serial
    private static final long serialVersionUID = -9105647382910564739L;

    private GoogleLinks google;
    private MetaLinks meta;

    @Data
    @Accessors(chain = true)
    public static class GoogleLinks implements Serializable {

        @Serial
        private static final long serialVersionUID = 6473829105647382911L;

        private String adAccountId;
        private String campaignId;
    }

    @Data
    @Accessors(chain = true)
    public static class MetaLinks implements Serializable {

        @Serial
        private static final long serialVersionUID = -2910564738291056474L;

        private String adAccountId;
        private String pageId;
        private String pixelId;
        private String campaignId;
    }
}
