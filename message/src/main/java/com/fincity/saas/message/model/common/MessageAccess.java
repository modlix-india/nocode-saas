package com.fincity.saas.message.model.common;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
public final class MessageAccess implements Serializable {

    @Serial
    private static final long serialVersionUID = 7935465831584064526L;

    private String appCode;
    private String clientCode;
    private ULong userId;
    private boolean hasAccessFlag;
    private ContextUser user;

    public static MessageAccess of(
            String appCode, String clientCode, ULong userId, boolean hasAccessFlag, ContextUser user) {
        return new MessageAccess()
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setUserId(userId)
                .setHasAccessFlag(hasAccessFlag)
                .setUser(user);
    }

    public static MessageAccess of(String appCode, String clientCode, ULong userId, boolean hasAccessFlag) {
        return of(appCode, clientCode, userId, hasAccessFlag, null);
    }

    public static MessageAccess of(String appCode, String clientCode, boolean hasAccessFlag) {
        return of(appCode, clientCode, null, hasAccessFlag, null);
    }

    public static MessageAccess ofNull() {
        return of(null, null, null, false, null);
    }

    public static MessageAccess of(ContextAuthentication ca) {
        return of(
                ca.getUrlAppCode(),
                ca.getClientCode(),
                ULongUtil.valueOf(ca.getUser().getId()),
                true,
                ca.getUser());
    }
}
