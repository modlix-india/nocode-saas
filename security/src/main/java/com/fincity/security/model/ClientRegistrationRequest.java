package com.fincity.security.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
public class ClientRegistrationRequest implements Serializable {

	private static final long serialVersionUID = 2510675233197533873L;

	private String clientName;
	private String localeCode;
	private ULong userId;
	private String userName;
	private String emailId;
	private String phoneNumber;
	private String firstName;
	private String lastName;
	private String middleName;
	private String password;
	private boolean businessClient;
	private String businessType;
	private String code;
	private String subDomain;
	private String socialToken;
	private String socialRefreshToken;
	private LocalDateTime socialTokenExpiresAt;
	private ULong socialIntegrationId;

	public void setSocialTokenExpiresAt(String socialTokenExpiresAt) {
        if (socialTokenExpiresAt != null && !socialTokenExpiresAt.isEmpty()) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
                this.socialTokenExpiresAt = LocalDateTime.parse(socialTokenExpiresAt, formatter);
            } catch (DateTimeParseException e) {
                // If parsing fails, try ISO_LOCAL_DATE_TIME format (yyyy-MM-ddTHH:mm:ss)
				this.socialTokenExpiresAt = LocalDateTime.parse(socialTokenExpiresAt);
			}
		}
	}
}
	