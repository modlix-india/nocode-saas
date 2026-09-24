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

		// Null-tolerant: this ordering went unused until the sign-in client picker
		// started sorting on it, and a comparator that throws would turn a single
		// half-populated client into a failed sign-in for the whole account.
		// Nameless entries sort last rather than blowing up the list.
		String mine = this.client == null ? null : this.client.getName();
		String theirs = o == null || o.getClient() == null ? null : o.getClient()
		        .getName();

		if (mine == null)
			return theirs == null ? 0 : 1;

		if (theirs == null)
			return -1;

		return mine.compareToIgnoreCase(theirs);
	}
}
