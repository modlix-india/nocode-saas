package com.fincity.saas.entity.processor.model.filter;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketFilter extends BaseProcessorFilter<TicketFilter> {

    @Serial
    private static final long serialVersionUID = 5855585254647170890L;

    private List<ULong> productIds;
}
