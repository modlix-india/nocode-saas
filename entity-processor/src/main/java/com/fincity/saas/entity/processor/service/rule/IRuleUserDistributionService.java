package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.entity.processor.dao.rule.BaseUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IRuleUserDistributionService<D extends BaseRuleDto<U, D>, U extends BaseUserDistributionDto<U>> {

    default <R extends UpdatableRecord<R>, O extends BaseUserDistributionDAO<R, U>>
            Optional<BaseUserDistributionService<R, U, O>> getUserDistributionService() {
        return Optional.empty();
    }

    default boolean isUserDistributionEnabled() {
        return this.getUserDistributionService().isPresent();
    }

    default Mono<List<U>> createUserDistribution(ProcessorAccess access, D created, D requestEntity) {
        return this.getUserDistributionService()
                .map(service -> service.createDistributions(
                                access, created.getId(), requestEntity.getUserDistributions())
                        .collectList())
                .orElseGet(() -> Mono.just(List.of()));
    }

    default Mono<List<U>> updateUserDistribution(ProcessorAccess access, D updated, D requestEntity) {
        return this.getUserDistributionService()
                .map(service -> requestEntity.isDistributionsEmpty()
                        ? service.getUserDistributions(access, updated.getId())
                        : service.updateDistributions(access, updated.getId(), requestEntity.getUserDistributions())
                                .collectList())
                .orElseGet(() -> Mono.just(List.of()));
    }

    default Mono<Void> deleteUserDistribution(ProcessorAccess access, D entity) {
        return this.getUserDistributionService()
                .map(service -> service.deleteByRuleId(access, entity.getId()))
                .orElseGet(Mono::empty)
                .then();
    }

    default Mono<List<D>> attachDistributions(ProcessorAccess access, List<D> rules) {
        return this.getUserDistributionService()
                .map(service -> Flux.fromIterable(rules)
                        .flatMap(rule -> service.getUserDistributions(access, rule.getId())
                                .doOnNext(rule::setUserDistributions)
                                .thenReturn(rule))
                        .collectList())
                .orElseGet(() -> Mono.just(rules));
    }

    default Mono<List<Map<String, Object>>> attachDistributionsEager(
            ProcessorAccess access, List<Map<String, Object>> rules) {
        return this.getUserDistributionService()
                .map(service -> Flux.fromIterable(rules)
                        .flatMap(rule -> service.getUserDistributions(access, (ULong) rule.get(AbstractDTO.Fields.id))
                                .map(userDistributions -> {
                                    rule.put(
                                            BaseRuleDto.Fields.userDistributions,
                                            userDistributions.stream()
                                                    .map(IClassConvertor::toMap)
                                                    .toList());
                                    return rule;
                                }))
                        .then(Mono.just(rules)))
                .orElseGet(() -> Mono.just(rules));
    }
}
