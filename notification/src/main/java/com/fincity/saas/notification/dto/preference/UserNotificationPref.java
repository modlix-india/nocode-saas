package com.fincity.saas.notification.dto.preference;

import java.io.Serial;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class UserNotificationPref extends UserPref<String, UserNotificationPref> {

	@Serial
	private static final long serialVersionUID = 6067523616163252576L;

	private String notificationName;

	@Override
	public String getValue() {
		return this.getNotificationName();
	}

	@Override
	public UserPref<String, UserNotificationPref> setValue(String value) {
		return this.setNotificationName(value);
	}
}
