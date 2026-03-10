package com.fincity.security.model;

import java.util.List;
import org.jooq.types.ULong;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SSLCertificateRenewalCandidate {

    private ULong urlId;
    private List<String> domainNames;
    private String organizationName;
    private int validityInMonths;
}
