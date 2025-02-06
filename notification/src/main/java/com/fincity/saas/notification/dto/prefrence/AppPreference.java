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
public class AppPreference extends NotificationPreference<AppPreference> {

	@Serial
	private static final long serialVersionUID = 6745559924764021655L;

	private ULong clientId;
	private ULong appId;
}
