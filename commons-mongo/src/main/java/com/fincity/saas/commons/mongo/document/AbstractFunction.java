package com.fincity.saas.commons.mongo.document;

import java.util.Map;

import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class AbstractFunction<D extends AbstractFunction<D>> extends AbstractOverridableDTO<D> {

    private static final long serialVersionUID = 2733397732360134939L;

    private Map<String, Object> definition; // NOSONAR
    private String executeAuth;

    protected AbstractFunction(D fun) {
        super(fun);
        this.definition = CloneUtil.cloneMapObject(fun.getDefinition());
        this.executeAuth = fun.getExecuteAuth();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<D> applyOverride(D base) {

        if (base != null)
            return DifferenceApplicator.apply(this.definition, base.getDefinition())
                    .map(a -> {
                        this.definition = (Map<String, Object>) a;
                        if (this.executeAuth == null)
                            this.executeAuth = base.getExecuteAuth();
                        return (D) this;
                    });

        return Mono.just((D) this);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<D> extractDifference(D base) {

        if (base == null)
            return Mono.just((D) this);

        return Mono.just(this)
                .flatMap(e -> DifferenceExtractor.extract(e.definition, base.getDefinition())
                        .map(k -> {
                            e.definition = (Map<String, Object>) k;

                            if (this.executeAuth != null && this.executeAuth.equals(base.getExecuteAuth()))
                                this.executeAuth = null;

                            return (D) e;
                        }));
    }
}
