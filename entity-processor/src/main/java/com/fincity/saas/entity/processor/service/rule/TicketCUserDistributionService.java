package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.entity.processor.dao.rule.TicketCUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketCUserDistribution;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.functions.IRepositoryProvider;
import com.fincity.saas.entity.processor.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketCUserDistributionsRecord;
import com.fincity.saas.entity.processor.util.ListFunctionRepository;
import com.fincity.saas.entity.processor.util.MapSchemaRepository;
import com.fincity.saas.entity.processor.util.SchemaUtil;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@IgnoreGeneration
public class TicketCUserDistributionService
        extends BaseUserDistributionService<
                EntityProcessorTicketCUserDistributionsRecord, TicketCUserDistribution, TicketCUserDistributionDAO>
        implements IRepositoryProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketCUserDistributionService.class);
    private static final String TICKET_C_USER_DISTRIBUTION = "ticketCUserDistribution";

    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final Gson gson;

    @Autowired
    @Lazy
    private TicketCUserDistributionService self;

    public TicketCUserDistributionService(Gson gson) {
        this.gson = gson;
    }

    @PostConstruct
    private void init() {
        this.functions.addAll(super.getCommonFunctions("TicketCUserDistribution", TicketCUserDistribution.class, gson));
    }

    @Override
    protected String getCacheName() {
        return TICKET_C_USER_DISTRIBUTION;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET_C_USER_DISTRIBUTION;
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {

        Map<String, Schema> schemas = new HashMap<>();
        try {
            Class<?> dtoClass = TicketCUserDistribution.class;
            String namespace = SchemaUtil.getNamespaceForClass(dtoClass);
            String name = dtoClass.getSimpleName();

            Schema schema = SchemaUtil.generateSchemaForClass(dtoClass);
            if (schema != null) {
                schemas.put(namespace + "." + name, schema);
                LOGGER.info("Generated schema for TicketCUserDistribution class: {}.{}", namespace, name);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to generate schema for TicketCUserDistribution class: {}", e.getMessage(), e);
        }

        if (!schemas.isEmpty()) {
            return Mono.just(new MapSchemaRepository(schemas));
        }

        return Mono.empty();
    }
}
