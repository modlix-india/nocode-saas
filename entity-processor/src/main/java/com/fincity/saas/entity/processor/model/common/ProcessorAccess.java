package com.fincity.saas.entity.processor.model.common;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.entity.processor.constant.BusinessPartnerConstant;
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
    private String loggedInClientCode;
    private ULong userId;
    private List<ULong> subOrg;
    private List<ULong> clientHierarchy;

    private boolean hasAccessFlag;
    private ContextUser user;
    private boolean hasBpAccess;

    public static ProcessorAccess of( // NOSONAR
            String appCode,
            String clientCode,
            String loggedInClientCode,
            BigInteger userId,
            boolean hasAccessFlag,
            List<BigInteger> subOrg,
            List<BigInteger> clientHierarchy,
            ContextUser user) {
        return new ProcessorAccess()
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setLoggedInClientCode(loggedInClientCode)
                .setUserId(userId)
                .setHasAccessFlag(hasAccessFlag)
                .setSubOrg(subOrg)
                .setClientHierarchy(clientHierarchy)
                .setUser(user)
                .setHasBpAccess(
                        user != null ? BusinessPartnerConstant.isBpManager(user.getAuthorities()) : Boolean.FALSE);
    }

    public static ProcessorAccess of(String appCode, String clientCode, boolean hasAccessFlag) {
        return of(appCode, clientCode, clientCode, null, hasAccessFlag, null, null, null);
    }

    public static ProcessorAccess ofNull() {
        return of(null, null, null, null, false, null, null, null);
    }

    public static ProcessorAccess of(
            ContextAuthentication ca, List<BigInteger> subOrg, List<BigInteger> clientHierarchy) {
        return of(
                ca.getUrlAppCode(),
                ca.getClientCode(),
                ca.getLoggedInFromClientCode(),
                ca.getUser().getId(),
                true,
                subOrg,
                clientHierarchy,
                ca.getUser());
    }

    public boolean isOutsideUser() {
        if (this.loggedInClientCode == null) return false;
        return this.clientCode.equals(this.loggedInClientCode);
    }

    private ProcessorAccess setUserId(BigInteger userId) {
        if (userId == null) return this;
        this.userId = ULongUtil.valueOf(userId);
        return this;
    }

    private ProcessorAccess setSubOrg(List<BigInteger> userSubOrg) {
        if (userSubOrg == null) return this;
        this.subOrg = userSubOrg.stream().map(ULongUtil::valueOf).toList();
        return this;
    }

    private ProcessorAccess setClientHierarchy(List<BigInteger> clientHierarchy) {
        if (clientHierarchy == null) return this;
        this.clientHierarchy = clientHierarchy.stream().map(ULongUtil::valueOf).toList();
        return this;
    }
}
