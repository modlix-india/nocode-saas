package com.fincity.saas.entity.processor.model.common;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;

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

    public static ProcessorAccess of(String appCode, String clientCode, ULong userId, boolean hasAccessFlag) {
        return new ProcessorAccess()
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setUserId(userId)
                .setHasAccessFlag(hasAccessFlag);
    }

    public static ProcessorAccess of(String appCode, String clientCode, ULong userId) {
        return of(appCode, clientCode, userId, false);
    }

    public static ProcessorAccess of(String appCode, String clientCode) {
        return of(appCode, clientCode, null);
    }

    public static ProcessorAccess of(String appCode, String clientCode, boolean hasAccessFlag) {
        return of(appCode, clientCode, null, hasAccessFlag);
    }

    public static ProcessorAccess ofNull() {
        return of(null, null, null, false);
    }

    public static ProcessorAccess of(Tuple2<Tuple3<String, String, ULong>, Boolean> access) {
        return of(access.getT1().getT1(), access.getT1().getT2(), access.getT1().getT3(), access.getT2());
    }

    public static ProcessorAccess of(Tuple3<String, String, ULong> access) {
        return of(access.getT1(), access.getT2(), access.getT3(), false);
    }

    public static ProcessorAccess of(Tuple3<String, String, ULong> hasAccess, boolean hasAccessFlag) {
        return of(hasAccess.getT1(), hasAccess.getT2(), hasAccess.getT3(), hasAccessFlag);
    }

    public static ProcessorAccess of(Tuple2<String, String> access, boolean hasAccessFlag) {
        return of(access.getT1(), access.getT2(), hasAccessFlag);
    }

    public static ProcessorAccess of(ContextAuthentication ca) {
        return of(
                ca.getUrlAppCode(),
                ca.getClientCode(),
                ULongUtil.valueOf(ca.getUser().getId()),
                true);
    }
}
