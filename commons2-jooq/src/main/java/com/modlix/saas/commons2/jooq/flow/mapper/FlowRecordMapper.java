package com.modlix.saas.commons2.jooq.flow.mapper;

import org.jooq.Record;
import org.jooq.RecordMapper;

import com.modlix.saas.commons2.jooq.flow.dto.AbstractFlowDTO;

public class FlowRecordMapper<R extends Record, T extends AbstractFlowDTO<?, ?>> implements RecordMapper<R, T> {
    @Override
    public T map(R record) {
        return null;
    }

    @Override
    public T apply(R record) {
        return RecordMapper.super.apply(record);
    }
}
