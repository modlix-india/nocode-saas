package com.fincity.saas.notification.dto;

import java.io.Serial;
import java.util.Map;

import com.fincity.saas.notification.dto.base.AbstractBaseDto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class Connection extends AbstractBaseDto<Connection> {

	@Serial
	private static final long serialVersionUID = 2999999794759806228L;

	private Map<String, Object> connectionDetails;
}
