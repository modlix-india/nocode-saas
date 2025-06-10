package com.fincity.saas.entity.processor.model.request.content;

import java.io.Serial;
import java.io.Serializable;

import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Identity;

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
}
