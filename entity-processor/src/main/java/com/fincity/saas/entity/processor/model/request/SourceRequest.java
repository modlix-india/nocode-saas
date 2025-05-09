package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.base.BaseValueTemplate;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SourceRequest extends BaseValueTemplate<SourceRequest> {

    @Serial
    private static final long serialVersionUID = 4052446554604503799L;
}
