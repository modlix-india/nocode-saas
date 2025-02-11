package com.fincity.saas.notification.dto;

import java.io.Serial;

import com.fincity.saas.notification.dto.base.BaseInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class NotificationType extends BaseInfo<NotificationType> {

	@Serial
	private static final long serialVersionUID = 2999999794759806228L;

}
