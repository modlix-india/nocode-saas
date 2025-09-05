package com.fincity.gateway;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;

@Service
public class GatewayMessageResourceService extends AbstractMessageService {

    public GatewayMessageResourceService() {

        super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH)));
    }
}