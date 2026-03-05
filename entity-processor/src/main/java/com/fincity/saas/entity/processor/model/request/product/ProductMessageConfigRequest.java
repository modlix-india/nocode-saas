package com.fincity.saas.entity.processor.model.request.product;

import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
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

    public boolean isValid() {
        return productId != null
                && !productId.isNull()
                && stageId != null
                && !stageId.isNull()
                && channel != null
                && templateIds != null
                && !templateIds.isEmpty();
    }
}
