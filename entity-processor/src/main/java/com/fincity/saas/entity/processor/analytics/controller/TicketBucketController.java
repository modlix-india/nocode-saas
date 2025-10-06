package com.fincity.saas.entity.processor.analytics.controller;

import com.fincity.saas.entity.processor.analytics.controller.base.BaseAnalyticsController;
import com.fincity.saas.entity.processor.analytics.dao.TicketBucketDAO;
import com.fincity.saas.entity.processor.analytics.model.DateStatusCount;
import com.fincity.saas.entity.processor.analytics.model.StatusEntityCount;
import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import com.fincity.saas.entity.processor.analytics.service.TicketBucketService;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/analytics/tickets")
public class TicketBucketController
        extends BaseAnalyticsController<EntityProcessorTicketsRecord, Ticket, TicketBucketDAO, TicketBucketService> {

    @PostMapping("/stage-counts/assigned-users")
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerAssignedUserStageCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerAssignedUserStageCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/stage-counts/sources/assigned-users")
    public Flux<DateStatusCount> getTicketPerAssignedUserStageSourceDateCount(
            @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service.getTicketPerAssignedUserStageSourceDateCount(effectiveFilter);
    }

    @PostMapping("/status-counts/assigned-users")
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerAssignedUserStatusCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerAssignedUserStatusCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/stage-counts/created-bys")
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerCreatedByStageCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerCreatedByStageCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/status-counts/created-bys")
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerCreatedByStatusCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerCreatedByStatusCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/stage-counts/clients")
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerClientIdStageCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerClientIdStageCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/status-counts/clients")
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerClientIdStatusCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerClientIdStatusCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/stage-counts/products")
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerProductIdStageCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerProjectStageCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/status-counts/products")
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerProjectStatusCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerProjectStatusCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }
}
