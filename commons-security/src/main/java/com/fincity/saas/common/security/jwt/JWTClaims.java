package com.fincity.saas.common.security.jwt;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString
public class JWTClaims implements Serializable {

	private static final String ONE_TIME = "oneTime";

	private static final long serialVersionUID = -6951503299014595535L;

	private BigInteger userId;
	private String hostName;
	private String port;
	private BigInteger loggedInClientId;
	private String loggedInClientCode;
	private boolean oneTime = false;

	public Map<String, Object> getClaimsMap() {

		Map<String, Object> map = new HashMap<>();

		map.put("userId", this.userId);
		map.put("hostName", this.hostName);
		map.put("port", this.port);

		if (this.loggedInClientId != null)
			map.put("loggedInClientId", this.loggedInClientId);

		if (this.loggedInClientCode != null)
			map.put("loggedInClientCode", this.loggedInClientCode);

		map.put(ONE_TIME, this.oneTime);

		return map;
	}

	public static JWTClaims from(Jws<Claims> parsed) {

		Claims claims = parsed.getBody();

		return new JWTClaims().setUserId(BigInteger.valueOf(claims.get("userId", Long.class)))
		        .setHostName(claims.get("hostName", String.class))
		        .setPort(claims.get("port", String.class))
		        .setLoggedInClientId(BigInteger.valueOf(claims.get("loggedInClientId", Long.class)))
		        .setLoggedInClientCode(claims.get("loggedInClientCode", String.class))
		        .setOneTime(claims.containsKey(ONE_TIME) ? claims.get(ONE_TIME, Boolean.class) : Boolean.FALSE);

	}
}
