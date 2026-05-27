package com.fincity.saas.entity.processor.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fincity.saas.entity.processor.enums.FunnelStage;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * One stage group (parent stage + its child statuses) with each node's chosen
 * funnel-stage tag. The funnel-tagging page posts the loaded stage tree back
 * with {@code funnelStage} set on whichever nodes the admin marked; the server
 * writes {@code FUNNEL_STAGE} on each. Unknown stage fields (name, code, ...)
 * are ignored so the page can post the tree verbatim.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelTagGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = 4471209853360021147L;

    private Ref parent;
    private List<Ref> child;

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ref implements Serializable {

        @Serial
        private static final long serialVersionUID = 8120945736612008314L;

        private Identity id;
        /** Null clears the tag (untag). */
        private FunnelStage funnelStage;
    }
}
