package com.fincity.saas.message.dto.call;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.util.NameUtil;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class Call extends BaseUpdatableDto<Call> {

    @Serial
    private static final long serialVersionUID = 6948416006208004030L;

    private String connectionName;
    private String callProvider;
    private Boolean isOutbound;
    private ULong exotelCallId;

    public Call() {
        super();
        this.relationsMap.put(Fields.exotelCallId, MessageSeries.EXOTEL_CALL.getTable());
    }

    public Call(Call call) {
        super(call);
        this.connectionName = call.connectionName;
        this.callProvider = call.callProvider;
        this.isOutbound = call.isOutbound;
        this.exotelCallId = call.exotelCallId;
    }

    public Call setCallProvider(String callProvider) {
        this.callProvider = NameUtil.normalizeToUpper(callProvider);
        return this;
    }
}
