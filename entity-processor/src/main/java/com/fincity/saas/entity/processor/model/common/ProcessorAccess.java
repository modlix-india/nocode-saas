package com.fincity.saas.entity.processor.model.common;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.dto.Client;
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
import reactor.util.function.Tuple3;

@Data
@Accessors(chain = true)
public final class ProcessorAccess implements Serializable {

    @Serial
    private static final long serialVersionUID = 7935465831584064526L;

    private String appCode;
    private String clientCode;
    private ULong userId;

    private boolean hasAccessFlag;
    private ContextUser user;
    private UserInheritanceInfo userInherit;
    private boolean hasBpAccess;

    public static ProcessorAccess of(
            String appCode,
            String clientCode,
            boolean hasAccessFlag,
            ContextUser user,
            UserInheritanceInfo userInherit) {

        if (user == null)
            return new ProcessorAccess()
                    .setAppCode(appCode)
                    .setClientCode(clientCode)
                    .setHasAccessFlag(hasAccessFlag)
                    .setHasBpAccess(Boolean.FALSE);

        return new ProcessorAccess()
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setUserId(ULongUtil.valueOf(user.getId()))
                .setHasAccessFlag(hasAccessFlag)
                .setUser(user)
                .setUserInherit(userInherit)
                .setHasBpAccess(BusinessPartnerConstant.isBpManager(user.getAuthorities()));
    }

    public static ProcessorAccess ofNull() {
        return of(null, null, false, null, null);
    }

    public static ProcessorAccess of(ContextAuthentication ca, UserInheritanceInfo userInherit) {
        return of(ca.getUrlAppCode(), ca.getClientCode(), true, ca.getUser(), userInherit);
    }

    public boolean isOutsideUser() {
        if (userInherit != null)
            return BusinessPartnerConstant.CLIENT_LEVEL_TYPE_BP.equals(this.userInherit.clientLevelType);

        return false;
    }

    public String getEffectiveClientCode() {
        return isOutsideUser() ? this.getUserInherit().getManagedClientCode() : this.getClientCode();
    }

    @Data
    @Accessors(chain = true)
    public static class UserInheritanceInfo implements Serializable {

        @Serial
        private static final long serialVersionUID = 6610191598069369190L;

        private String clientLevelType;

        private String loggedInClientCode;
        private ULong loggedInClientId;

        private String managedClientCode;
        private ULong managedClientId;

        private List<ULong> subOrg;
        private List<ULong> managingClientIds;

        public static UserInheritanceInfo of(
                ContextAuthentication ca, Tuple3<List<BigInteger>, List<BigInteger>, Client> userInheritTup) {
            UserInheritanceInfo userInheritanceInfo = new UserInheritanceInfo()
                    .setClientLevelType(ca.getClientLevelType())
                    .setLoggedInClientCode(ca.getLoggedInFromClientCode())
                    .setLoggedInClientId(ULongUtil.valueOf(ca.getLoggedInFromClientId()))
                    .setSubOrg(userInheritTup.getT1())
                    .setManagingClientIds(userInheritTup.getT2());

            if (userInheritTup.getT3().getId() != null)
                userInheritanceInfo
                        .setManagedClientCode(userInheritTup.getT3().getCode())
                        .setManagedClientId(
                                ULongUtil.valueOf(userInheritTup.getT3().getId()));

            return userInheritanceInfo;
        }

        private UserInheritanceInfo setSubOrg(List<BigInteger> userSubOrg) {
            if (userSubOrg == null) return this;
            this.subOrg = userSubOrg.stream().map(ULongUtil::valueOf).toList();
            return this;
        }

        private UserInheritanceInfo setManagingClientIds(List<BigInteger> managingClientIds) {
            if (managingClientIds == null) return this;
            this.managingClientIds =
                    managingClientIds.stream().map(ULongUtil::valueOf).toList();
            return this;
        }
    }
}
