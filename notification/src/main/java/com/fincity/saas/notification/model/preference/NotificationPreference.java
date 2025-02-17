package com.fincity.saas.notification.model.preference;

import java.math.BigInteger;
import java.util.Map;

import com.fincity.saas.notification.enums.PreferenceLevel;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class NotificationPreference {

	private BigInteger appId;
	private BigInteger userId;

}
