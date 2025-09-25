package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.model.base.PerCount;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PerDateCount extends PerCount<PerDateCount> implements Serializable {

    @Serial
    private static final long serialVersionUID = 8924787024207584607L;

    private LocalDate date;
}
