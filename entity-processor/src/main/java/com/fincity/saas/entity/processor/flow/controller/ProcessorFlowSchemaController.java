package com.fincity.saas.entity.processor.flow.controller;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.flow.controller.schema.FlowSchemaController;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.flow.dao.ProcessorFlowSchemaDAO;
import com.fincity.saas.entity.processor.flow.dto.ProcessorFlowSchema;
import com.fincity.saas.entity.processor.flow.service.ProcessorFlowSchemaService;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorFlowSchemaRecord;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/flow/schema")
public class ProcessorFlowSchemaController
        extends FlowSchemaController<
                EntityProcessorFlowSchemaRecord,
                ULong,
                ProcessorFlowSchema,
                ProcessorFlowSchemaDAO,
                ProcessorFlowSchemaService> {

    @GetMapping("/entity-series/{id}")
    public Mono<ResponseEntity<Schema>> readByEntitySeries(
            @PathVariable(PATH_VARIABLE_ID) final ULong id, @RequestBody EntitySeries entitySeries) {
        return this.service.getFlowSchema(id, entitySeries).map(ResponseEntity::ok);
    }
}
