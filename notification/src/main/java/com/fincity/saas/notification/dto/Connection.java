package com.fincity.saas.notification.dto;

import java.io.Serial;
import java.util.Map;

import com.fincity.saas.notification.dto.base.BaseInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class Connection extends BaseInfo<Connection> {

	@Serial
	private static final long serialVersionUID = 3892399247592045964L;

	private Map<String, Object> connectionDetails;
}
