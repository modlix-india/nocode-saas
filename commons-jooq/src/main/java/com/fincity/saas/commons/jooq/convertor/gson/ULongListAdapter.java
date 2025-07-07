package com.fincity.saas.commons.jooq.convertor.gson;

import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.util.ULongUtil;

public class ULongListAdapter extends AbstractListAdapter<ULong> {

	public ULongListAdapter() {
		super(ULongUtil::valueOf);
	}

	@Override
	protected String serializeItem(ULong item) {
		return item.toBigInteger().toString();
	}
}
