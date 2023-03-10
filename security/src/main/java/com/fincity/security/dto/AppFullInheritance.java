package com.fincity.security.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class AppFullInheritance implements Serializable {

	private static final long serialVersionUID = -1505422039182636881L;
	
	private List<String> clients;
	private String baseClientCode;
}
