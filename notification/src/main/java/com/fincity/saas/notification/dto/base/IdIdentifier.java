package com.fincity.saas.notification.dto.base;

import org.jooq.types.ULong;

public interface IdIdentifier<T extends IdIdentifier<T>> {

	ULong getIdentifierId();

	T setIdentifierId(ULong identifierId);
}
