package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ProductRequest extends BaseRequest<ProductRequest> {

    @Serial
    private static final long serialVersionUID = 6940756756706631631L;

    private Identity productTemplateId;
    private Boolean forPartner = Boolean.FALSE;
    private FileDetail logoFileDetail;
    private FileDetail bannerFileDetail;
}
