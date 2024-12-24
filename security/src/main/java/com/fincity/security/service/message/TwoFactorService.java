package com.fincity.security.service.message;

import java.io.Serial;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.model.OtpMessageVars;
import com.fincity.security.service.SecurityMessageResourceService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lombok.Builder;
import lombok.Data;
import reactor.core.publisher.Mono;

@Service
public class TwoFactorService implements MessageService {

	private static final Logger logger = LoggerFactory.getLogger(TwoFactorService.class);

	@Autowired
	private SecurityMessageResourceService messageResourceService;

	@Autowired
	private Gson gson;

	private static final String TWO_FACTOR_API_URL = "https://2factor.in/API/R1/";

	private static final String FROM = "APYASA";

	private static final String OTP_TEMPLATE = "SAOtpMessage";

	private static final String OTP_PEID = "PEID";

	private static final String OTP_CTID = "CTID";

	private static final String ERROR = "Error";

	@Value("${sms.provider.2factor.api.key}")
	private String apiKey;

	private final WebClient webClient;

	public TwoFactorService() {
		this.webClient = WebClient.create(TWO_FACTOR_API_URL);
	}

	@Override
	public Mono<Boolean> sendOtpMessage(String phoneNumber, OtpMessageVars otpMessageVars) {

		if (Boolean.FALSE.equals(checkForKeys())) {
			logger.info("Sending fOTP message to {} with details {}", phoneNumber, otpMessageVars);
			return Mono.just(Boolean.TRUE);
		}

		TransactionalSms request = TransactionalSms.builder()
				.apikey(apiKey)
				.to(new String[]{phoneNumber})
				.templateName(OTP_TEMPLATE)
				.var1(otpMessageVars.getOtpCode())
				.peid(OTP_PEID)
				.ctid(OTP_CTID)
				.build();

		return FlatMapUtil.flatMapMono(

				() -> webClient.post()
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.body(BodyInserters.fromFormData(request.toBodyMap()))
						.accept(MediaType.APPLICATION_JSON)
						.retrieve()
						.toEntity(String.class),

				response -> {

					if (response.getStatusCode().isError()) {
						logger.debug("SMS otp failed: {}", response.getBody());
						return messageResourceService.getMessage(SecurityMessageResourceService.SMS_OTP_ERROR)
								.flatMap(msg -> Mono.error(new GenericException(HttpStatus.resolve(response.getStatusCode().value()), msg)));
					}

					JsonObject res = gson.fromJson(response.getBody(), JsonElement.class).getAsJsonObject();

					if (res.isEmpty() || res.get("Status").getAsString().equals(ERROR)) {
						logger.debug("SMS otp failed: {}", res);
						return messageResourceService.getMessage(SecurityMessageResourceService.SMS_OTP_ERROR)
								.flatMap(msg -> Mono.error(new GenericException(HttpStatus.resolve(response.getStatusCode().value()), msg)));
					}

					return Mono.just(Boolean.TRUE);
				}).onErrorResume(error -> {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
					SecurityMessageResourceService.SMS_OTP_ERROR);
		});
	}

	@Data
	@Builder(builderClassName = "TransactionalSmsBuilder")
	public static class TransactionalSms implements Serializable {

		@Serial
		private static final long serialVersionUID = 8274499861428315738L;

		private static final String TSMS_MODULE = "TRANS_SMS";

		private String apikey;
		private String[] to;
		private String templateName;
		private MultiValueMap<String, String> vars;
		private String scheduletime;
		private String peid;
		private String ctid;

		public static class TransactionalSmsBuilder {

			public TransactionalSmsBuilder vars(MultiValueMap<String, String> vars, boolean override) {
				if (this.vars == null) {
					this.vars = new LinkedMultiValueMap<>();
				}
				if (override) {
					this.vars.putAll(vars);
				} else {
					this.vars.addAll(vars);
				}
				return this;
			}

			private TransactionalSmsBuilder addVar(int varNumber, String value) {
				if (this.vars == null) {
					this.vars = new LinkedMultiValueMap<>();
				}
				this.vars.add("var" + varNumber, value);
				return this;
			}

			public TransactionalSmsBuilder var1(String value) {
				return addVar(1, value);
			}

			public TransactionalSmsBuilder var2(String value) {
				return addVar(2, value);
			}

			public TransactionalSmsBuilder var3(String value) {
				return addVar(3, value);
			}
		}

		public MultiValueMap<String, String> toBodyMap() {

			int expectedSize = this.vars == null ? 8 : 8 + this.vars.size();

			MultiValueMap<String, String> body = new LinkedMultiValueMap<>(expectedSize);
			body.add("module", TSMS_MODULE);
			body.add("apikey", apikey);
			body.add("to", String.join(",", to));
			body.add("from", FROM);
			body.add("templatename", templateName);

			if (scheduletime != null)
				body.add("scheduletime", scheduletime);
			if (peid != null)
				body.add("peid", peid);
			if (ctid != null)
				body.add("ctid", ctid);
			if (vars != null && !vars.isEmpty())
				body.addAll(vars);

			return body;
		}
	}

	private Boolean checkForKeys() {
		if (StringUtil.safeIsBlank(apiKey)) {
			logger.error("ERROR: Two Factor Credentials Missing: apiKey");
			return Boolean.FALSE;
		}
		return Boolean.TRUE;
	}

}
