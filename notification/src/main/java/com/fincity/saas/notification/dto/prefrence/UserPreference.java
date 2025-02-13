package com.fincity.saas.notification.dto.prefrence;

import java.io.Serial;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class UserPreference extends NotificationPreference<UserPreference> {

	@Serial
	private static final long serialVersionUID = 5340826954624052288L;

	private ULong userId;

	@Override
	public ULong getIdentifierId() {
		return this.getUserId();
	}

	@Override
	public UserPreference setIdentifierId(ULong identifierId) {
		return this.setUserId(identifierId);
	}
}
