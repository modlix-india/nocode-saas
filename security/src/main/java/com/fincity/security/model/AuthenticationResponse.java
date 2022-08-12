package com.fincity.security.model;

import java.time.LocalDateTime;

import com.fincity.security.dto.User;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AuthenticationResponse {

	private User user;

	private String accessToken;
	private LocalDateTime accessTokenExpiryAt;
}
