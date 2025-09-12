package com.fincity.saas.entity.processor.model.request.content;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.enums.content.ContentEntitySeries;
import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

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
    private ULong userId;

    public T setOwnerId(Identity ownerId) {
        this.ownerId = ownerId;
        return (T) this;
    }

    public T setTicketId(Identity ticketId) {
        this.ticketId = ticketId;
        return (T) this;
    }

    public boolean hasContent() {
        return this.getContent() != null && !this.getContent().trim().isEmpty();
    }

    @JsonIgnore
    public ContentEntitySeries getContentEntitySeries() {
        if (this.ticketId != null && !this.ticketId.isNull()) return ContentEntitySeries.TICKET;

        if (this.ownerId != null && this.ownerId.isNull()) return ContentEntitySeries.OWNER;

        if (this.userId != null) return ContentEntitySeries.USER;

        return ContentEntitySeries.TICKET;
    }
}
