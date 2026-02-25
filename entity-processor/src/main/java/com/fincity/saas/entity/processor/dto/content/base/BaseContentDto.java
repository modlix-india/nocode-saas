package com.fincity.saas.entity.processor.dto.content.base;

import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.eager.relations.resolvers.field.UserFieldResolver;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.content.ContentEntitySeries;
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
public abstract class BaseContentDto<T extends BaseContentDto<T>> extends BaseProcessorDto<T> {

    @Serial
    private static final long serialVersionUID = 5174424228629814984L;

    private String content;
    private Boolean hasAttachment;
    private ContentEntitySeries contentEntitySeries;
    private ULong ownerId;
    private ULong ticketId;
    private ULong userId;

    protected BaseContentDto() {
        super();
        this.relationsMap.put(Fields.ticketId, EntitySeries.TICKET.getTable());
        this.relationsMap.put(Fields.ownerId, EntitySeries.OWNER.getTable());
        this.relationsResolverMap.put(UserFieldResolver.class, Fields.userId);
    }

    protected BaseContentDto(BaseContentDto<T> baseContentDto) {
        super(baseContentDto);
        this.content = baseContentDto.content;
        this.hasAttachment = baseContentDto.hasAttachment;
        this.contentEntitySeries = baseContentDto.contentEntitySeries;
        this.ownerId = baseContentDto.ownerId;
        this.ticketId = baseContentDto.ticketId;
        this.userId = baseContentDto.userId;
    }

    public T setOwnerId(ULong ownerId) {
        this.ownerId = ownerId;
        return (T) this;
    }

    public T setTicketId(ULong ticketId) {
        this.ticketId = ticketId;
        return (T) this;
    }

    public T setUserId(ULong userId) {
        this.userId = userId;
        return (T) this;
    }
}
