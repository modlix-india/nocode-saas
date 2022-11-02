package com.fincity.saas.commons.security.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Data
@RequiredArgsConstructor
@Slf4j
public class ClientUrlPattern {

	public static final Pattern URL_PATTERN = Pattern
	        .compile("(http\\:|https\\:){0,1}\\/\\/([a-z0-9\\.]+)([\\:]([0-9]{0,5})){0,1}");

	private final String identifier;
	private final String clientCode;
	private final String urlPattern;
	private final String appCode;

	private Tuple3<Protocol, String, Integer> hostnPort = null;

	public Tuple3<Protocol, String, Integer> getHostnPort() {

		if (hostnPort != null || this.urlPattern == null || this.urlPattern.isBlank())
			return this.hostnPort;

		this.makeHostnPort();
		return this.hostnPort;
	}

	@JsonIgnore
	public ClientUrlPattern makeHostnPort() {

		if (hostnPort != null || this.urlPattern == null || this.urlPattern.isBlank())
			return this;

		Matcher matcher = URL_PATTERN.matcher(this.urlPattern.trim()
		        .toLowerCase());

		if (!matcher.find()) {
			this.hostnPort = Tuples.of(Protocol.ANY, "", -1);
			return this;
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
				log.error("Unable to parse port in the url {} ", this.urlPattern);
			}
		}

		this.hostnPort = Tuples.of(protocol, matcher.group(2), intPort);
		return this;
	}

	public boolean isValidClientURLPattern(String finScheme, String finHost, String finPort) {
		try {
			return isValidClientURLPattern(finScheme, finHost, Integer.valueOf(finPort));
		} catch (Exception ex) {
			log.error("Unable to parse port numeber {} in the url '{}' ", finPort, this.urlPattern);
			return false;
		}
	}

	public boolean isValidClientURLPattern(String finScheme, String finHost, Integer finPort) {

		Tuple3<Protocol, String, Integer> tuple = this.getHostnPort();

		if (tuple == null)
			return false;

		String scheme = finScheme.toLowerCase();

		if (!tuple.getT2()
		        .equals(finHost.toLowerCase()))
			return false;

		int checkPort = -1;

		if (tuple.getT1() == Protocol.HTTPS) {

			if (!scheme.startsWith("https:"))
				return false;

			checkPort = 443;
		} else if (tuple.getT1() == Protocol.HTTP) {

			if (!scheme.startsWith("http"))
				return false;

			checkPort = 80;
		}

		if (tuple.getT3() != -1)
			checkPort = tuple.getT3();

		return checkPort == -1 || finPort == checkPort;
	}

	public enum Protocol {
		HTTP, HTTPS, ANY
	}
}
