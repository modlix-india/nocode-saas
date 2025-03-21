package com.fincity.saas.notification.model.response;

import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NotificationErrorInfo {

	private HttpStatus errorCode;
	private String messageId;
	private String errorMessage;
	private String transId;

	public <T extends GenericException> NotificationErrorInfo(T exception) {
		errorCode = exception.getStatusCode();
		messageId = exception.getMessage();
		errorMessage = exception.getLocalizedMessage();
	}
}
