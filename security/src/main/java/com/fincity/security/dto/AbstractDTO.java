package com.fincity.security.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AbstractDTO<I extends Serializable, U extends Serializable> implements Serializable {

	private static final long serialVersionUID = 7628167781600904807L;

	private I id;
	private LocalDateTime createdAt;
	private U createdBy;
}
