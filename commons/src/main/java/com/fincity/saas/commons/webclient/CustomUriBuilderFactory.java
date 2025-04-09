package com.fincity.saas.commons.webclient;

import org.springframework.web.util.DefaultUriBuilderFactory;

public class CustomUriBuilderFactory extends DefaultUriBuilderFactory {

    public CustomUriBuilderFactory(String baseUrl, WebClientEncodingModes encodingMode) {

        super(baseUrl);

        EncodingMode mode =
                switch (encodingMode) {
                    case TEMPLATE_AND_VALUES -> EncodingMode.TEMPLATE_AND_VALUES;
                    case URI_COMPONENT -> EncodingMode.URI_COMPONENT;
                    case VALUES_ONLY -> EncodingMode.VALUES_ONLY;
                    case MANUAL_VALUES_ENCODED, NONE -> EncodingMode.NONE;
                    default -> EncodingMode.TEMPLATE_AND_VALUES;
                };

        this.setEncodingMode(mode);
    }
}
