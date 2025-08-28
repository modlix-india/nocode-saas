package com.fincity.saas.ui.model;


import com.fincity.saas.ui.document.MobileApp;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@Accessors(chain = true)
public class MobileAppStatusUpdateRequest implements java.io.Serializable {

    private MobileApp.Status status;
    private String errorMessage;
    private String androidAppURL;
    private String iosAppURL;
}
