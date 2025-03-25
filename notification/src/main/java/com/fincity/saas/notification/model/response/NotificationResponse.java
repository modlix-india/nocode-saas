package com.fincity.saas.notification.model.response;

import java.io.Serial;
import java.io.Serializable;

import org.springframework.http.HttpStatus;

import com.fincity.saas.notification.model.SendRequest;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NotificationResponse implements Serializable {

	@Serial
	private static final long serialVersionUID = 7271697827921768050L;

	private String transId;
	private HttpStatus status;
	private String details;

	private static NotificationResponse of(String transId, HttpStatus status, String details) {
		return new NotificationResponse().setTransId(transId).setStatus(status).setDetails(details);
	}

	private static NotificationResponse of(String transId, HttpStatus status) {
		return new NotificationResponse().setTransId(transId).setStatus(status).setDetails(status.getReasonPhrase());
	}

	public static NotificationResponse ofSuccess(SendRequest request) {
		return of(request.getCode(), HttpStatus.OK);
	}

	public static NotificationResponse ofError(SendRequest request) {
		return request.isError()
				? of(request.getCode(), request.getErrorInfo().getErrorCode(), request.getErrorInfo().getMessageId())
				: of(request.getCode(), HttpStatus.INTERNAL_SERVER_ERROR);
	}
}
