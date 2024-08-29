package com.fincity.saas.ui.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fincity.saas.commons.mongo.difference.IDifferentiable;
import com.fincity.saas.ui.enums.RedirectionType;

import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

@Data
@NoArgsConstructor
public class RedirectionDefinition implements Serializable, IDifferentiable<RedirectionDefinition> {

    @Serial
    private static final long serialVersionUID = 7335074228662664368L;

    private RedirectionType redirectionType;
    private String targetUrl;
    private String shortCode;
    private boolean isShortUrl;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private boolean override;

    @Override
    public Mono<RedirectionDefinition> extractDifference(RedirectionDefinition inc) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'extractDifference'");
    }

    @Override
    public Mono<RedirectionDefinition> applyOverride(RedirectionDefinition override) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'applyOverride'");
    }
}
