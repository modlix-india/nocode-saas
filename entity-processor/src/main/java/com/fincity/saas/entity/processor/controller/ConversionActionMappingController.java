package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.ConversionActionMappingDAO;
import com.fincity.saas.entity.processor.dto.ConversionActionMapping;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorConversionActionMappingRecord;
import com.fincity.saas.entity.processor.model.request.ConversionActionMappingRequest;
import com.fincity.saas.entity.processor.model.request.SeedConversionDefaultsRequest;
import com.fincity.saas.entity.processor.service.ConversionActionMappingService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/conversion/action/mappings")
public class ConversionActionMappingController
        extends BaseUpdatableController<
                EntityProcessorConversionActionMappingRecord,
                ConversionActionMapping,
                ConversionActionMappingDAO,
                ConversionActionMappingService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ConversionActionMapping>> createRequest(
            @RequestBody ConversionActionMappingRequest request) {
        return this.service.createRequest(request).map(ResponseEntity::ok);
    }

    @PostMapping("/seed/defaults")
    public Mono<ResponseEntity<List<ConversionActionMapping>>> seedDefaults(
            @RequestBody SeedConversionDefaultsRequest request) {
        return this.service.seedDefaults(request).map(ResponseEntity::ok);
    }
}
