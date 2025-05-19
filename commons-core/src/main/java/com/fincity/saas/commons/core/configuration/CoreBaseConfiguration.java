package com.fincity.saas.commons.core.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.core.service.connection.email.EmailService;
import com.fincity.saas.commons.core.service.connection.rest.RestService;
import com.fincity.saas.commons.core.service.file.TemplateConversionService;
import com.fincity.saas.commons.core.service.security.ClientUrlService;
import com.fincity.saas.commons.core.service.security.ContextService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.core.feign.IFeignFilesService;
import com.google.gson.Gson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreBaseConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public Gson gson() {
        return new Gson();
    }

    // These services should be provided by the implementing server
    protected AppDataService appDataService;
    protected RestService restService;
    protected ContextService userContextService;
    protected IFeignSecurityService securityService;
    protected IFeignFilesService fileService;
    protected ClientUrlService clientUrlService;
    protected EmailService emailService;
    protected TemplateConversionService templateConversionService;

    // Getters for the services
    public AppDataService getAppDataService() {
        return appDataService;
    }

    public RestService getRestService() {
        return restService;
    }

    public ContextService getUserContextService() {
        return userContextService;
    }

    public IFeignSecurityService getSecurityService() {
        return securityService;
    }

    public IFeignFilesService getFileService() {
        return fileService;
    }

    public ClientUrlService getClientUrlService() {
        return clientUrlService;
    }

    public EmailService getEmailService() {
        return emailService;
    }

    public TemplateConversionService getTemplateConversionService() {
        return templateConversionService;
    }
}