package com.fincity.saas.commons.core.model;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.enums.EventActionTaskType;
import com.fincity.saas.commons.difference.IDifferentiable;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class EventActionTask implements IDifferentiable<EventActionTask>, Comparable<EventActionTask>, Serializable {

    @Serial
    private static final long serialVersionUID = 667994849634307890L;

    private String key;
    private Integer order;
    private EventActionTaskType type;
    private Map<String, Object> parameters; // NOSONAR

    public EventActionTask(EventActionTask eat) {
        this.key = eat.key;
        this.order = eat.order;
        this.type = eat.type;
        this.parameters = CloneUtil.cloneMapObject(eat.parameters);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<EventActionTask> extractDifference(EventActionTask inc) {
        return FlatMapUtil.flatMapMono(
                        () -> DifferenceExtractor.extract(inc.parameters, this.parameters)
                                .defaultIfEmpty(Map.of()),
                        params -> {
                            EventActionTask eat = new EventActionTask();

                            // this is the BASE and inc is the DERIVED: a nested
                            // IDifferentiable is reached as existing.extractDifference(incoming),
                            // which is the OPPOSITE way round from a document's
                            // extractDifference(base). Both halves used to be written the
                            // document way, so a derived client that changed an order or a
                            // type had the BASE's value stored as its delta - the save
                            // reported success and the value snapped back. The old order
                            // test also wrote a delta when the derived said nothing at all.
                            if (!CommonsUtil.safeEquals(this.order, inc.order)) eat.order = inc.order;

                            if (!CommonsUtil.safeEquals(this.type, inc.type)) eat.type = inc.type;

                            eat.parameters = (Map<String, Object>) params;

                            return Mono.just(eat);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventActionTask.extractDifference"));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<EventActionTask> applyOverride(EventActionTask override) {
        if (override == null) return Mono.just(this);

        // flatMapMonoWithNull, not flatMapMono: DifferenceApplicator.apply returns
        // EMPTY when both maps are null or empty, and an empty step short-circuits
        // the whole chain - so a task carrying no parameters DISAPPEARED from the
        // merged result instead of being applied. extractDifference already guards
        // the same call with defaultIfEmpty; this is the other half of that.
        return FlatMapUtil.flatMapMonoWithNull(
                        () -> DifferenceApplicator.apply(this.parameters, override.parameters), params -> {
                            if (this.key == null) this.key = override.key;

                            if (this.order == null) this.order = override.order;

                            if (this.type == null) this.type = override.type;

                            this.setParameters((Map<String, Object>) params);

                            return Mono.just(this);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventActionTask.applyOverride"));
    }

    @Override
    public int compareTo(EventActionTask o) {
        int compValue = Integer.compare(this.order == null ? 0 : this.order, o.order == null ? 0 : o.order);
        return compValue == 0 ? this.key.compareTo(o.key) : compValue;
    }
}
