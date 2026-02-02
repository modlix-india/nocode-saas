package com.fincity.saas.commons.core.model;

import com.fincity.saas.commons.core.document.Connection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
@AllArgsConstructor
public class NotificationConnectionDetails implements Serializable {

    private boolean inApp;
    private Connection mail;
}
