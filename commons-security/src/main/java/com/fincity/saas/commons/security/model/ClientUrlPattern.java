package com.fincity.saas.commons.security.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.util.StringUtil;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Data
@RequiredArgsConstructor
public class ClientUrlPattern {

	public static final Pattern URL_PATTERN = Pattern
	        .compile("(http\\:\\/\\/|https\\:\\/\\/)?([^\\:]+)(\\:(\\d{0,5}))?");

	private final String identifier;
	private final String clientCode;
	private final String urlPattern;
	private final String appCode;

	private Tuple3<Protocol, String, String> hostnPort = null;

	public Tuple3<Protocol, String, String> getHostnPort() {

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
			this.hostnPort = Tuples.of(Protocol.ANY, "", "");
			return this;
		}

		String group = matcher.group(1);
		Protocol protocol = Protocol.ANY;

		if (StringUtil.safeEquals(group, "http://"))
			protocol = Protocol.HTTP;
		else if (StringUtil.safeEquals(group, "https://"))
			protocol = Protocol.HTTPS;

		String port = matcher.group(4);

		this.hostnPort = Tuples.of(protocol, matcher.group(2), port == null ? "" : port);
		return this;
	}

	public boolean isValidClientURLPattern(String finHost, String finPort) {

		Tuple3<Protocol, String, String> tuple = this.getHostnPort();

		if (tuple == null)
			return false;

		if (!tuple.getT2()
		        .equals(finHost.toLowerCase()))
			return false;

		if (tuple.getT3()
		        .isBlank() || StringUtil.safeIsBlank(finPort))
			return true;

		return tuple.getT3()
		        .contains(finPort) || finPort.contains(tuple.getT3());
	}

	public enum Protocol {
		HTTP, HTTPS, ANY
	}
}
