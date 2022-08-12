package com.fincity.security.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.types.ULong;

import lombok.Data;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Data
public class ClientURLPattern {

	public static final Pattern URL_PATTERN = Pattern
	        .compile("(http\\:|https\\:){0,1}\\/\\/([a-z0-9\\.]+)([\\:]([0-9]{0,5})){0,1}");

	private ULong clientId;
	private String urlPattern;

	private Tuple3<Protocol, String, Integer> hostnPort = null;

	public Tuple3<Protocol, String, Integer> getHostnPort() {

		if (hostnPort != null || this.urlPattern == null || this.urlPattern.isBlank())
			return this.hostnPort;

		Matcher matcher = URL_PATTERN.matcher(this.urlPattern.trim()
		        .toLowerCase());

		if (!matcher.find()) {
			this.hostnPort = Tuples.of(Protocol.ANY, "", -1);
			return this.hostnPort;
		}

		String group = matcher.group(1);
		Protocol protocol = Protocol.ANY;

		if (group.equals("http:"))
			protocol = Protocol.HTTP;
		else if (group.equals("https:"))
			protocol = Protocol.HTTPS;

		String port = matcher.group(4);
		Integer intPort = -1;
		if (port != null) {
			try {
				intPort = Integer.parseInt(port);
			} catch (Exception ex) {
				// Need to ignore the exception and move on.
			}
		}

		this.hostnPort = Tuples.of(protocol, matcher.group(2), intPort);
		return this.hostnPort;
	}

	public enum Protocol {
		HTTP, HTTPS, ANY
	}
}
