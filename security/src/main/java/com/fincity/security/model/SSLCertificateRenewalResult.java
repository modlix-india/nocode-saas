package com.fincity.security.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SSLCertificateRenewalResult {

    private int renewedCount;
    private int failedCount;
    private List<String> errors = new ArrayList<>();

    public SSLCertificateRenewalResult addError(String error) {
        this.errors.add(error);
        return this;
    }
}
