package com.fincity.saas.entity.processor.model.request;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Create a Google Ads conversion action in the client's connected account so a
 * fresh client can provision one without leaving Modlix. {@code name} is
 * required; {@code category} defaults to {@code DEFAULT}. The account
 * ({@code customerId}/{@code loginCustomerId}) is resolved from the client's
 * existing Google campaigns unless supplied explicitly.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CreateGoogleConversionActionRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -6620918374455103928L;

    private String name;
    private String category;
    private String type;
    private String countingType;
    private Integer clickThroughLookbackWindowDays;
    private Boolean primaryForGoal;
    private String customerId;
    private String loginCustomerId;
}
