package com.fincity.saas.message.configuration.message.whatsapp;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WhatsappApiConfig {

    @Getter
    private static final ApiVersion apiVersion = ApiVersion.LATEST;

    @Getter
    private static final String BASE_DOMAIN = "https://graph.facebook.com/";
}
