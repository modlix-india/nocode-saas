package com.fincity.saas.notification.dto;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.notification.dto.base.BaseInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class Notification extends BaseInfo<Notification> {

	@Serial
	private static final long serialVersionUID = 5488955076177275391L;

	private ULong notificationTypeId;
	private ULong emailTemplateId;
	private ULong inAppTemplateId;
	private ULong smsTemplateId;
	private ULong pushTemplateId;

}
