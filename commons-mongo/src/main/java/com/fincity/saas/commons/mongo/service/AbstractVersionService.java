package com.fincity.saas.commons.mongo.service;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.support.PageableExecutionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.mongo.document.Version;
import com.fincity.saas.commons.mongo.repository.VersionRepository;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractVersionService extends AbstractMongoDataService<String, Version, VersionRepository> {

    protected static final String READ = "READ";

    @Autowired
    protected FeignAuthenticationService securityService;

    @Autowired
    protected com.fincity.saas.commons.mongo.repository.InheritanceService inheritanceService;

    @Autowired
    protected ObjectMapper om;

    protected AbstractVersionService() {
        super(Version.class);
    }

    @Override
    public Mono<Version> read(String id) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> super.read(id),

                this::accessCheck,

                (ca, v, access) -> Mono.<Version>justOrEmpty(access.booleanValue() ? v : null))

            .contextWrite(Context.of(LogUtil.METHOD_NAME, "VersionService.read"));
    }

    protected Mono<Boolean> accessCheck(ContextAuthentication ca, Version v) {

        if (StringUtil.safeIsBlank(v.getObjectType()))
            return Mono.just(false);

        return FlatMapUtil.flatMapMono(
                () -> SecurityContextUtil.hasAuthority(
                    "Authorities." + this.mapAuthName(v.getObjectType()) + "_" + READ, ca.getAuthorities())
                    ? Mono.just(true)
                    : Mono.empty(),

                access -> {
                    if (ca.getClientCode()
                        .equals(v.getClientCode()))
                        return Mono.just(true);

                    return this.inheritanceService
                        .order(v.getObjectAppCode(), ca.getUrlClientCode(), ca.getClientCode())
                        .map(e -> e.contains(ca.getClientCode()));
                },

                (access, managed) -> {

                    if (!managed.booleanValue())
                        return Mono.empty();

                    return this.securityService.hasReadAccess(v.getObjectAppCode(), ca.getClientCode());
                })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "VersionService.accessCheck"))
            .defaultIfEmpty(false);
    }

    protected abstract String mapAuthName(String objectType);

    @Override
    protected Mono<String> getLoggedInUserId() {

        return SecurityContextUtil.getUsersContextAuthentication()
            .map(ContextAuthentication::getUser)
            .map(ContextUser::getId)
            .map(Object::toString);
    }

    public Mono<Page<Version>> readPagePerObjectId(String id, Query query) {

        FilterCondition idCondition = new FilterCondition().setField("object.id")
            .setValue(id)
            .setOperator(FilterConditionOperator.EQUALS);

        AbstractCondition condition = query.getCondition() == null || query.getCondition()
            .isEmpty() ? idCondition
            : new ComplexCondition().setConditions(List.of(idCondition, query.getCondition()))
            .setOperator(ComplexConditionOperator.AND);

        var sort = query.getSort()
            .equals(Query.DEFAULT_SORT) ? Sort.by(Order.desc("createdAt")) : query.getSort();

        Pageable page = PageRequest.of(query.getPage(), query.getSize(), sort);

        return FlatMapUtil.flatMapMono(

                () -> this.filter(condition),

                crit -> Mono.just((new org.springframework.data.mongodb.core.query.Query(crit)).skip(page.getOffset())
                    .limit(page.getPageSize())
                    .with(page.getSort())),

                (crit, dataQuery) -> this.mongoTemplate
                    .getCollection(this.mongoTemplate.getCollectionName(Version.class)),

                (crit, dataQuery, collection) -> {

                    var bsonCondition = dataQuery.getQueryObject();

                    Flux<Document> findFlux = makeResultFlux(query, page, collection, bsonCondition);

                    return findFlux.map(e -> this.mongoTemplate.getConverter()
                            .read(Version.class, e))
                        .collectList();
                },

                (crit, dataQuery, collection, list) -> Mono
                    .just((new org.springframework.data.mongodb.core.query.Query(crit)).with(page.getSort())),

                (crit, dataQuery, collection, list, countQuery) -> this.mongoTemplate.count(countQuery, Version.class),

                (crit, dataQuery, collection, list, countQuery, count) -> Mono
                    .just(PageableExecutionUtils.<Version>getPage(list, page, () -> count)))
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "versionService.readPagePerObjectId"));
    }

    private Flux<Document> makeResultFlux(Query query, Pageable page, MongoCollection<Document> collection,
                                          Document bsonCondition) {

        Flux<Document> findFlux;

        if (query.getFields() == null || query.getFields()
            .isEmpty()) {
            FindPublisher<Document> publisher = collection.find(bsonCondition);

            if (!Query.DEFAULT_SORT.equals(page.getSort()))
                publisher.sort(this.sort(page.getSort()));

            findFlux = Flux.from(publisher.skip((int) page.getOffset())
                .limit(page.getPageSize()));
        } else {

            List<Bson> pipeLines = new ArrayList<>(List.of(Aggregates.match(bsonCondition)));

            Bson sort = null;
            if (!Query.DEFAULT_SORT.equals(page.getSort()))
                sort = this.sort(page.getSort());

            if (sort != null)
                pipeLines.add(Aggregates.sort(sort));

            pipeLines.add(Aggregates.project(Projections.fields(query.getExcludeFields()
                .booleanValue() ? Projections.exclude(query.getFields())
                : Projections.include(query.getFields()))));
            pipeLines.add(Aggregates.skip((int) page.getOffset()));
            pipeLines.add(Aggregates.limit(page.getPageSize()));

            var agg = collection.aggregate(pipeLines);

            findFlux = Flux.from(agg);
        }
        return findFlux;
    }

    private Bson sort(Sort sort) {
        if (sort == null)
            return null;

        if (sort.equals(Query.DEFAULT_SORT))
            return null;

        BsonDocument document = new BsonDocument();
        for (Order e : sort.toList()) {
            document.append(e.getProperty(), new BsonInt32(e.getDirection() == Direction.DESC ? -1 : 1));
        }
        return document;
    }

    public Mono<Long> deleteBy(String appCode, String clientCode, String objectType) {
        return this.repo.deleteByObjectAppCodeAndClientCodeAndObjectType(appCode, clientCode, objectType);
    }
}
