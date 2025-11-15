package com.fincity.saas.entity.processor.model.request.product;

import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ProductPartnerUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1579341573105546310L;

    private List<IdAndValue<Identity, Boolean>> productPartnerActive;
}
