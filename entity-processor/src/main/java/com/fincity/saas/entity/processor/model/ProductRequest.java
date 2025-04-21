package com.fincity.saas.entity.processor.model;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ProductRequest implements Serializable {

	@Serial
	private static final long serialVersionUID = 6940756756706631631L;

	private String name;
	private String description;
	private String status;
	private String subStatus;

}
