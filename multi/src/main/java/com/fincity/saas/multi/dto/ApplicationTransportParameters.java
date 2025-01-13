package com.fincity.saas.multi.dto;

import java.nio.file.Path;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ApplicationTransportParameters {
    private Path file;
    private String accessToken;
    private String forwardedHost;
    private String forwardedPort;
    private String clientCode;
    private String headerAppCode;
    private Boolean isBaseApp;
    private String cc;
    private String appCode;
}
