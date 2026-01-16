package com.fincity.saas.entity.processor.analytics.model;

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
public class EntityStatusCount implements Serializable {

    @Serial
    private static final long serialVersionUID = 8009993639629612554L;

    private ULong id;

    private String name;

    private CountPercentage totalCount;

    private List<StatusEntityCount> statusCount;
}
