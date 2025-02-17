package com.fincity.saas.notification.dto.preference;

import java.io.Serial;
import java.io.Serializable;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.util.UniqueUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public abstract class UserPref<T, I extends UserPref<T, I>> extends AbstractUpdatableDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 2391422436782345284L;

	private ULong appId;
	private ULong userId;
	private String code = UniqueUtil.shortUUID();
	private boolean enabled = Boolean.FALSE;

	public abstract T getValue();

	public abstract UserPref<T, I> setValue(T value);
}
