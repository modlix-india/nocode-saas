package com.fincity.sass.worker.dto;

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
}
