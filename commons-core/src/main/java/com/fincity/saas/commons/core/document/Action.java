package com.fincity.saas.commons.core.document;

import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.util.CloneUtil;
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
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "actionFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Action extends AbstractOverridableDTO<Action> {

    @Serial
    private static final long serialVersionUID = 3425030507970576753L;

    private String functionNamespace;
    private String functionName;
    private Map<String, String> properties;

    public Action(Action action) {
        super(action);
        this.functionName = action.functionName;
        this.functionNamespace = action.functionNamespace;
        this.properties = CloneUtil.cloneMapObject(action.properties);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Action> applyOverride(Action base) {
        if (base == null) return Mono.just(this);

        return DifferenceApplicator.apply(this.properties, base.properties)
                .defaultIfEmpty(Map.of())
                .map(a -> {
                    this.properties = (Map<String, String>) a;
                    if (this.functionNamespace == null) this.functionNamespace = base.functionNamespace;
                    if (this.functionName == null) this.functionName = base.functionName;
                    return this;
                });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Action> makeOverride(Action base) {
        if (base == null) return Mono.just(this);

        return Mono.just(this).flatMap(e -> DifferenceExtractor.extract(e.properties, base.properties)
                .map(k -> {
                    e.properties = (Map<String, String>) k;

                    if (this.functionNamespace != null && this.functionNamespace.equals(base.functionNamespace))
                        this.functionNamespace = null;

                    if (this.functionName != null && this.functionName.equals(base.functionName))
                        this.functionName = null;

                    return e;
                }));
    }
}
