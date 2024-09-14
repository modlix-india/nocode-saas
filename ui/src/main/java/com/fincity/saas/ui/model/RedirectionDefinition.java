package com.fincity.saas.ui.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import org.springframework.http.HttpMethod;

import com.fincity.saas.commons.mongo.difference.IDifferentiable;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.ui.enums.RedirectionType;

import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Data
@NoArgsConstructor
public class RedirectionDefinition implements Serializable, IDifferentiable<RedirectionDefinition> {

	@Serial
	private static final long serialVersionUID = 7335074228662664368L;

	private RedirectionType redirectionType;
	private HttpMethod targetHttpMethod;
	private String targetUrl;
	private String shortCode;

	private LocalDateTime validFrom;
	private LocalDateTime validUntil;

	@Override
	public Mono<RedirectionDefinition> extractDifference(RedirectionDefinition inc) {

		if (inc == null) {
			return Mono.just(this);
		}

		RedirectionDefinition diff = new RedirectionDefinition();

		if (!CommonsUtil.safeEquals(this.redirectionType, inc.redirectionType)) {
			diff.setRedirectionType(inc.redirectionType);
		}
		if (!CommonsUtil.safeEquals(this.targetHttpMethod, inc.targetHttpMethod)) {
			diff.setTargetHttpMethod(inc.targetHttpMethod);
		}
		if (!CommonsUtil.safeEquals(this.targetUrl, inc.targetUrl)) {
			diff.setTargetUrl(inc.targetUrl);
		}
		if (!CommonsUtil.safeEquals(this.shortCode, inc.shortCode)) {
			diff.setShortCode(inc.shortCode);
		}
		if (!CommonsUtil.safeEquals(this.validFrom, inc.validFrom)) {
			diff.setValidFrom(inc.validFrom);
		}
		if (!CommonsUtil.safeEquals(this.validUntil, inc.validUntil)) {
			diff.setValidUntil(inc.validUntil);
		}

		return Mono.just(diff)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "RedirectionDefinition.extractDifference"));
	}

	@Override
	public Mono<RedirectionDefinition> applyOverride(RedirectionDefinition override) {
		if (override == null) {
			return Mono.just(this);
		}

		if (override.getRedirectionType() != null) {
			this.setRedirectionType(override.getRedirectionType());
		}
		if (override.getTargetHttpMethod() != null) {
			this.setTargetHttpMethod(override.getTargetHttpMethod());
		}
		if (override.getTargetUrl() != null) {
			this.setTargetUrl(override.getTargetUrl());
		}
		if (override.getShortCode() != null) {
			this.setShortCode(override.getShortCode());
		}
		if (override.getValidFrom() != null) {
			this.setValidFrom(override.getValidFrom());
		}
		if (override.getValidUntil() != null) {
			this.setValidUntil(override.getValidUntil());
		}

		return Mono.just(this)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "RedirectionDefinition.applyOverride"));
	}
}
