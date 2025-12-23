package com.fincity.saas.entity.processor.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.gson.AbstractConditionTypeAdapter;
import com.fincity.saas.entity.processor.gson.EmailTypeAdapter;
import com.fincity.saas.entity.processor.gson.IdentityTypeAdapter;
import com.fincity.saas.entity.processor.gson.PageTypeAdapter;
import com.fincity.saas.entity.processor.gson.PageableTypeAdapter;
import com.fincity.saas.entity.processor.gson.PhoneNumberTypeAdapter;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.google.gson.Gson;

import jakarta.annotation.PostConstruct;

@Configuration
public class ProcessorConfiguration extends AbstractJooqBaseConfiguration implements ISecurityConfiguration {

    protected ProcessorMessageResourceService processorMessageResourceService;

    protected ProcessorConfiguration(
            ProcessorMessageResourceService messageResourceService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.processorMessageResourceService = messageResourceService;
    }

    @Override
    @PostConstruct
    public void initialize() {
        super.initialize(processorMessageResourceService);
        Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
        FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {
            if (name != null)
                log.debug(
                        "{} - {}",
                        name,
                        !name.startsWith("full-") && v.length() > 500 ? v.substring(0, 500) + "..." : v);
            else
                log.debug(v);
        }));
    }

    @Override
    public Gson makeGson() {
        Gson baseGson = super.makeGson();

        // Create adapter instances that need Gson reference
        ArraySchemaType.ArraySchemaTypeAdapter arraySchemaTypeAdapter = new ArraySchemaType.ArraySchemaTypeAdapter();
        AdditionalType.AdditionalTypeAdapter additionalTypeAdapter = new AdditionalType.AdditionalTypeAdapter();

        // Build Gson with all adapters
        Gson gson = baseGson.newBuilder()
                .registerTypeAdapter(Identity.class, new IdentityTypeAdapter())
                .registerTypeAdapter(Email.class, new EmailTypeAdapter())
                .registerTypeAdapter(PhoneNumber.class, new PhoneNumberTypeAdapter())
                .registerTypeAdapter(Pageable.class, new PageableTypeAdapter())
                .registerTypeAdapterFactory(new AbstractConditionTypeAdapter.Factory())
                .registerTypeAdapterFactory(new PageTypeAdapter.Factory())
                .registerTypeAdapter(Type.class, new Type.SchemaTypeAdapter())
                .registerTypeAdapter(AdditionalType.class, additionalTypeAdapter)
                .registerTypeAdapter(ArraySchemaType.class, arraySchemaTypeAdapter)
                .create();

        // Set Gson on adapters that need it (for circular references)
        arraySchemaTypeAdapter.setGson(gson);
        additionalTypeAdapter.setGson(gson);

        return gson;
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
        return this.springSecurityFilterChain(
                http,
                authService,
                this.objectMapper,
                "/api/entity/processor/core/function/**",
                "/api/entity/processor/core/functions/repositoryFilter",
                "/api/entity/processor/core/functions/repositoryFind",
                "/api/entity/processor/open/**",
                "/api/entity/processor/products/internal",
                "/api/entity/processor/products/internal/**",
                "/api/entity/processor/tickets/req/DCRM",
                "/api/entity/processor/functions/repositoryFilter",
                "/api/entity/processor/functions/repositoryFind",
                "/api/entity/processor/schemas/repositoryFind",
                "/api/entity/processor/schemas/repositoryFilter");
    }
}
