package com.fincity.saas.notification.dto.prefrence;

import java.io.Serial;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.notification.dto.base.ChannelDetails;
import com.fincity.saas.notification.dto.base.IdIdentifier;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.enums.NotificationType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public abstract class NotificationPreference<T extends NotificationPreference<T>> extends AbstractUpdatableDTO<ULong, ULong>
		implements IdIdentifier<T>, ChannelDetails<Boolean, T> {

	@Serial
	private static final long serialVersionUID = 4007524811937317620L;

	private static final Map<NotificationChannelType, Boolean> DEFAULT_PREF =
			Arrays.stream(NotificationChannelType.values())
					.collect(Collectors.toMap(
							Function.identity(),
							type -> false,
							(a, b) -> b,
							() -> new EnumMap<>(NotificationChannelType.class)
					));

	private ULong appId;
	private NotificationType notificationTypeId;

	private Map<NotificationChannelType, Boolean> preferences = DEFAULT_PREF;

	public boolean has(NotificationChannelType notificationChannelType) {
		return this.preferences.getOrDefault(notificationChannelType, false);
	}

	@SuppressWarnings("unchecked")
	@Override
	public T setChannelValue(NotificationChannelType channelType, Boolean enabled) {
		if (channelType == NotificationChannelType.DISABLED) {
			if (Boolean.TRUE.equals(enabled))
				disableAll();
			this.preferences.put(NotificationChannelType.DISABLED, enabled);
		} else {
			this.preferences.put(channelType, enabled);
			updateDisabledState();
		}
		return (T) this;
	}

	private void disableAll() {
		this.preferences.replaceAll((type, value) -> false);
	}

	private void updateDisabledState() {

		boolean allDisabled = this.preferences.entrySet().stream()
				.filter(entry -> entry.getKey() != NotificationChannelType.DISABLED)
				.noneMatch(entry -> Boolean.TRUE.equals(entry.getValue()));

		this.preferences.put(NotificationChannelType.DISABLED, allDisabled);
	}
}
