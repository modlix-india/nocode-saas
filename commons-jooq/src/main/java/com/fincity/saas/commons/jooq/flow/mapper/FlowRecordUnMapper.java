package com.fincity.saas.commons.jooq.flow.mapper;

import org.jooq.Record;
import org.jooq.RecordUnmapper;
import org.jooq.exception.MappingException;

import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowDTO;

public class FlowRecordUnMapper<R extends Record, T extends AbstractFlowDTO<?, ?>> implements RecordUnmapper<T, R> {
	@Override
	public R unmap(T source) throws MappingException {
		return null;
	}
}
