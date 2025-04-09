package com.fincity.saas.entity.collector.dto;

import lombok.*;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntityIntegration {
    private Long id;
    private String clientCode;
    private String appCode;
    private String target;
    private String secondaryTarget;
    private String inSource;
    private InSourceType inSourceType;

    public enum InSourceType {
        FACEBOOK_FORM,
        GOOGLE_FORM,
        WEBSITE
    }
}
