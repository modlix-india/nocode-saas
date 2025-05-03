package com.fincity.saas.commons.core.document;

import com.fincity.saas.commons.core.model.EventActionTask;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.util.CloneUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;
import java.io.Serial;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "eventActionFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class EventAction extends AbstractOverridableDTO<EventAction> {

    @Serial
    private static final long serialVersionUID = 8419515774158611099L;

    private Map<String, EventActionTask> tasks;

    public EventAction(EventAction obj) {
        super(obj);
        this.tasks = CloneUtil.cloneMapObject(obj.tasks);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<EventAction> applyOverride(EventAction base) {
        if (base == null)
            return Mono.just(this);

        return DifferenceApplicator.apply(this.tasks, base.tasks)
                .defaultIfEmpty(Map.of())
                .map(a -> {
                    this.tasks = (Map<String, EventActionTask>) a;
                    return this;
                });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<EventAction> makeOverride(EventAction base) {
        if (base == null)
            return Mono.just(this);

        return Mono.just(this)
                .flatMap(e -> DifferenceExtractor.extract(e.tasks, base.tasks).map(k -> {
                    e.tasks = (Map<String, EventActionTask>) k;
                    return e;
                }));
    }
}
