package com.fincity.saas.notification.model.response;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.notification.util.IClassConverter;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NotificationErrorInfo implements IClassConverter, Serializable {

    @Serial
    private static final long serialVersionUID = 3134271005996293784L;

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
