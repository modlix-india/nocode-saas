package com.fincity.saas.entity.processor.model.common;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
public final class ProcessorAccess implements Serializable {

    @Serial
    private static final long serialVersionUID = 7935465831584064526L;

    private String appCode;
    private String clientCode;
    private ULong userId;
    private List<ULong> subOrg;

    private boolean hasAccessFlag;

    public static ProcessorAccess of(
            String appCode, String clientCode, ULong userId, boolean hasAccessFlag, List<BigInteger> subOrg) {
        return new ProcessorAccess()
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setUserId(userId)
                .setHasAccessFlag(hasAccessFlag)
                .setSubOrg(subOrg);
    }

    public static ProcessorAccess of(String appCode, String clientCode, boolean hasAccessFlag) {
        return of(appCode, clientCode, null, hasAccessFlag, null);
    }

    public static ProcessorAccess ofNull() {
        return of(null, null, null, false, null);
    }

    public static ProcessorAccess of(ContextAuthentication ca, List<BigInteger> subOrg) {
        return of(
                ca.getUrlAppCode(),
                ca.getClientCode(),
                ULongUtil.valueOf(ca.getUser().getId()),
                true,
                subOrg);
    }

    public ProcessorAccess setSubOrg(List<BigInteger> userSubOrg) {
        if (userSubOrg == null) return this;
        this.subOrg = userSubOrg.stream().map(ULongUtil::valueOf).toList();
        return this;
    }
}
