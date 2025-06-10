package com.fincity.saas.entity.processor.dao.content.base;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;

public abstract class BaseContentDAO<R extends UpdatableRecord<R>, D extends BaseContentDto<D>> extends BaseDAO<R, D> {

    private static final String CONTENT = "CONTENT";
    private static final String HAS_ATTACHMENT = "HAS_ATTACHMENT";

    protected final Field<String> contentField;
    protected final Field<Boolean> hasAttachmentField;

    protected BaseContentDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.contentField = flowTable.field(CONTENT, String.class);
        this.hasAttachmentField = flowTable.field(HAS_ATTACHMENT, Boolean.class);
    }
}
