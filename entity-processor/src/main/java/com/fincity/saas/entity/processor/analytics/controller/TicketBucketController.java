package com.fincity.saas.entity.processor.analytics.controller;

import com.fincity.saas.entity.processor.analytics.controller.base.BaseAnalyticsController;
import com.fincity.saas.entity.processor.analytics.dao.TicketBucketDAO;
import com.fincity.saas.entity.processor.analytics.model.BucketFilter;
import com.fincity.saas.entity.processor.analytics.model.StatusCount;
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
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/analytics/tickets")
public class TicketBucketController
        extends BaseAnalyticsController<EntityProcessorTicketsRecord, Ticket, TicketBucketDAO, TicketBucketService> {

    @PostMapping("/stage-counts/assigned-users")
    public Mono<ResponseEntity<Page<StatusCount>>> getTicketPerAssignedUserStatusCount(
            Pageable pageable, @RequestBody(required = false) BucketFilter filter) {

        BucketFilter effectiveFilter = (filter == null) ? new BucketFilter() : filter;

        return this.service
                .getTicketPerAssignedUserStatusCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/stage-counts/created-bys")
    public Mono<ResponseEntity<Page<StatusCount>>> getTicketPerCreatedByStatusCount(
            Pageable pageable, @RequestBody(required = false) BucketFilter filter) {

        BucketFilter effectiveFilter = (filter == null) ? new BucketFilter() : filter;

        return this.service
                .getTicketPerCreatedByStatusCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/stage-counts/clients")
    public Mono<ResponseEntity<Page<StatusCount>>> getTicketPerClientIdStatusCount(
            Pageable pageable, @RequestBody(required = false) BucketFilter filter) {

        BucketFilter effectiveFilter = (filter == null) ? new BucketFilter() : filter;

        return this.service
                .getTicketPerClientIdStatusCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/stage-counts/products")
    public Mono<ResponseEntity<Page<StatusCount>>> getTicketPerProductIdStatusCount(
            Pageable pageable, @RequestBody(required = false) BucketFilter filter) {

        BucketFilter effectiveFilter = (filter == null) ? new BucketFilter() : filter;

        return this.service
                .getTicketPerProductIdStatusCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }
}
