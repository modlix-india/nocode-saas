package com.fincity.saas.entity.processor.model.common;

import com.fincity.saas.entity.processor.enums.EntitySeries;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
public class ActivityObject implements Serializable {

    @Serial
    private static final long serialVersionUID = 4444332415146628697L;

    private EntitySeries entitySeries;
    private ULong id;
    private Map<String, Object> data;

    public static ActivityObject of(EntitySeries entitySeries, ULong id, Map<String, Object> data) {
        return new ActivityObject().setEntitySeries(entitySeries).setId(id).setData(data);
    }

    public static ActivityObject ofTicket(ULong id, Map<String, Object> data) {
        return new ActivityObject()
                .setEntitySeries(EntitySeries.TICKET)
                .setId(id)
                .setData(data);
    }

    public static ActivityObject ofTicket(ULong id) {
        return new ActivityObject().setEntitySeries(EntitySeries.TICKET).setId(id);
    }
}
