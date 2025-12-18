package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.entity.processor.dao.rule.TicketCUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketCUserDistribution;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketCUserDistributionsRecord;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
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

    private static final String TICKET_C_USER_DISTRIBUTION = "ticketCUserDistribution";

    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final ClassSchema classSchema = ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());
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
        return this.defaultSchemaRepositoryFor(TicketCUserDistribution.class, classSchema);
    }
}
