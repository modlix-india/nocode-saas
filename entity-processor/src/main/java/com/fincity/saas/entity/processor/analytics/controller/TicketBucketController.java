package com.fincity.saas.entity.processor.analytics.controller;

import com.fincity.saas.entity.processor.analytics.controller.base.BaseAnalyticsController;
import com.fincity.saas.entity.processor.analytics.dao.TicketBucketDAO;
import com.fincity.saas.entity.processor.analytics.model.DateStatusCount;
import com.fincity.saas.entity.processor.analytics.model.EntityStatusCount;
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

    @PostMapping({"/assigned-users/stages", "/stage-counts/assigned-users"})
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerAssignedUserStageCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerAssignedUserStageCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping({"/assigned-users/stages/sources/dates", "/stage-counts/sources/assigned-users"})
    public Flux<DateStatusCount> getTicketPerAssignedUserStageSourceDateCount(
            @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service.getTicketPerAssignedUserStageSourceDateCount(effectiveFilter);
    }

    @PostMapping({"/assigned-users/statuses", "/status-counts/assigned-users"})
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerAssignedUserStatusCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerAssignedUserStatusCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping({"/created-bys/stages", "/stage-counts/created-bys"})
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerCreatedByStageCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerCreatedByStageCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping({"/created-bys/stages/totals/dates", "/stage-counts/created-bys/unique"})
    public Flux<DateStatusCount> getTicketPerCreatedByStageTotalWithUniqueCreatedBy(
            @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service.getTicketPerCreatedByStageTotalWithUniqueCreatedBy(effectiveFilter);
    }

    @PostMapping({"/created-bys/stages/totals/dates/client-id", "/stage-counts/created-bys/unique/client-id"})
    public Flux<DateStatusCount> getTicketPerCreatedByStageTotalWithUniqueCreatedByWithClientId(
            @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service.getTicketPerCreatedByStageTotalWithUniqueCreatedByWithClientId(effectiveFilter);
    }

    @PostMapping({"/created-bys/statuses", "/status-counts/created-bys"})
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerCreatedByStatusCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerCreatedByStatusCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping({"/clients/stages", "/stage-counts/clients"})
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerClientIdStageCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerClientIdStageCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping({"/clients/statuses", "/status-counts/clients"})
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerClientIdStatusCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerClientIdStatusCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping({"/products/stages", "/stage-counts/products"})
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerProductIdStageCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerProjectStageCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping({"/products/statuses", "/status-counts/products"})
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerProjectStatusCount(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerProjectStatusCount(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping({"/products/stages/clients/me", "/stage-counts/products/clients/me"})
    public Mono<ResponseEntity<Page<StatusEntityCount>>> getTicketPerProjectStageCountForLoggedInClient(
            Pageable pageable, @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service
                .getTicketPerProjectStageCountForLoggedInClient(pageable, effectiveFilter)
                .map(ResponseEntity::ok);
    }

    @PostMapping({"/clients/products/stages", "/stage-counts/products/clients"})
    public Flux<EntityStatusCount> getTicketPerProductStageAndClientIdCount(
            @RequestBody(required = false) TicketBucketFilter filter) {

        TicketBucketFilter effectiveFilter = (filter == null) ? new TicketBucketFilter() : filter;

        return this.service.getTicketPerProductStageAndClientIdCount(effectiveFilter);
    }
}
