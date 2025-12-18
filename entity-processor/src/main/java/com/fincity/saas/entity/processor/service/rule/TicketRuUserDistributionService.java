package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.entity.processor.dao.rule.TicketRuUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketRuUserDistributionsRecord;
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
public class TicketRuUserDistributionService
        extends BaseUserDistributionService<
                EntityProcessorTicketRuUserDistributionsRecord, TicketRuUserDistribution, TicketRuUserDistributionDAO>
        implements IRepositoryProvider {

    private static final String TICKET_RU_USER_DISTRIBUTION = "ticketRUUserDistribution";

    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final ClassSchema classSchema = ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());
    private final Gson gson;

    @Autowired
    @Lazy
    private TicketRuUserDistributionService self;

    public TicketRuUserDistributionService(Gson gson) {
        this.gson = gson;
    }

    @PostConstruct
    private void init() {
        this.functions.addAll(
                super.getCommonFunctions("TicketRuUserDistribution", TicketRuUserDistribution.class, gson));
    }

    @Override
    protected String getCacheName() {
        return TICKET_RU_USER_DISTRIBUTION;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET_RU_USER_DISTRIBUTION;
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(TicketRuUserDistribution.class, classSchema);
    }
}
