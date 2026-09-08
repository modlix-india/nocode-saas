package com.fincity.saas.entity.processor.model.request.product;

import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ProductMessageConfigRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 6823790716459331018L;

    private Identity productId;
    private Identity stageId;
    private Identity statusId;

    private MessageChannelType channel;

    private List<ULong> templateIds;

    private Integer startingOrder;

    /**
     * Optional per-template asset, for a welcome packet.
     *
     * <p>Keyed by template id rather than positional against {@link #templateIds}, because a
     * parallel list would silently attach the wrong brochure to the wrong template the first time
     * someone reordered one and not the other. Duplicate template ids are already rejected, so the
     * key is unique by construction.
     *
     * <p>Absent or empty means a plain text-only config, which is the pre-existing behaviour.
     */
    private Map<ULong, MessageAsset> assets;

    public boolean isValid() {
        return productId != null
                && !productId.isNull()
                && stageId != null
                && !stageId.isNull()
                && channel != null
                && templateIds != null
                && !templateIds.isEmpty();
    }

    /** The asset attached to one template in the packet, if any. */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class MessageAsset implements Serializable {

        @Serial
        private static final long serialVersionUID = 1189004427739155614L;

        private FileDetail fileDetail;
        private String caption;
    }
}
