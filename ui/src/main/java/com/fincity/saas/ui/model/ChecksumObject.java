package com.fincity.saas.ui.model;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ChecksumObject implements Serializable {

	private static final long serialVersionUID = -3804873941419862809L;

	private String checkSum;
	private String objectString;
	private Map<String, String> headers;

	public ChecksumObject(String objectString) {
		this.objectString = objectString;
	}

	public String getCheckSum() {

		if (this.checkSum != null)
			return this.checkSum;

		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] messageDigest = md.digest(objectString.getBytes());
			BigInteger no = new BigInteger(1, messageDigest);
			this.checkSum = no.toString(16);
		} catch (Exception ex) {
			checkSum = Integer.toHexString(objectString.hashCode());
		}

		return this.checkSum;
	}
}
