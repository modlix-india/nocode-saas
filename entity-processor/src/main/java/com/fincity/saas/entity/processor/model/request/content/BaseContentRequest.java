package com.fincity.saas.entity.processor.model.request.content;

import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class BaseContentRequest<T extends BaseContentRequest<T>> extends BaseRequest<T>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 4055371621770626606L;

    private String content;
    private Boolean hasAttachment;
    private Identity ownerId;
    private Identity ticketId;

    public T setOwnerId(Identity ownerId) {
        this.ownerId = ownerId;
        return (T) this;
    }

    public T setTicketId(Identity ticketId) {
        this.ticketId = ticketId;
        return (T) this;
    }
}
