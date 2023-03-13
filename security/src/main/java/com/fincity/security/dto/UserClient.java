package com.fincity.security.dto;

import java.io.Serializable;

import org.jooq.types.ULong;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserClient implements Serializable, Comparable<UserClient> {

	private static final long serialVersionUID = -4277053785496371349L;

	private ULong userId;
	private Client client;

	@Override
	public int compareTo(UserClient o) {

		return this.client.getName()
		        .compareToIgnoreCase(o.getClient()
		                .getName());
	}
}
