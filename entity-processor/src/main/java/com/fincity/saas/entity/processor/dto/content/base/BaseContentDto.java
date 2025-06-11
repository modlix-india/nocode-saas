package com.fincity.saas.entity.processor.dto.content.base;

import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.model.request.content.BaseContentRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;
import org.springframework.data.annotation.Version;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public abstract class BaseContentDto<Q extends BaseContentRequest<Q>, T extends BaseContentDto<Q, T>> extends BaseDto<T>
        implements IEntitySeries {

    @Serial
    private static final long serialVersionUID = 5174424228629814984L;

    @Version
    private int version = 1;

    private String content;
    private Boolean hasAttachment;
    private ULong ownerId;
    private ULong ticketId;

    public abstract T of(Q contentRequest);

    public boolean isTicketContent() {
        return ticketId != null;
    }

    public boolean isOwnerContent() {
        if (isTicketContent()) return Boolean.FALSE;
        return ownerId != null;
    }

    public T setOwnerId(ULong ownerId) {
        this.ownerId = ownerId;
        return (T) this;
    }

    public T setTicketId(ULong ticketId) {
        this.ticketId = ticketId;
        return (T) this;
    }
}
