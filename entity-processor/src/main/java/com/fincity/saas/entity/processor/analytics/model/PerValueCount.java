package com.fincity.saas.entity.processor.analytics.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jooq.types.ULong;

@Data
@AllArgsConstructor
public class PerValueCount {

    private ULong id;
    private String value;
    private Long count;

    public static List<ULong> getAllIds(List<PerValueCount> perValueCountList) {
        return perValueCountList.stream().map(PerValueCount::getId).distinct().toList();
    }
}
