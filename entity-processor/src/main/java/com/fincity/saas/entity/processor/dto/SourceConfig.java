package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SourceConfig extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 7291045823901234567L;

    private String appCode;
    private String clientCode;
    private String name;
    private ULong parentId;
    private Integer displayOrder = 0;
    private boolean callSource;
    private boolean defaultSource;
    private boolean active = true;

    private transient List<SourceConfig> children = new ArrayList<>();
}
