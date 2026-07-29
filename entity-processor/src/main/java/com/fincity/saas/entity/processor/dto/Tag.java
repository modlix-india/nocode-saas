package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Tag extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 5489104582390123456L;

    private String appCode;
    private String clientCode;
    private String name;
    private boolean active = true;
    private String color;
    private String icon;
}
