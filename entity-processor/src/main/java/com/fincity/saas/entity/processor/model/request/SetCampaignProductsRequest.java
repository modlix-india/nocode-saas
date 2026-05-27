package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Body for {@code PUT /campaigns/{id}/products}: replaces the campaign's full
 * product link set (set semantics). An empty/null list clears all links.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class SetCampaignProductsRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 7142098563321470012L;

    private List<Identity> productIds;
}
