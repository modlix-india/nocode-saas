package com.fincity.saas.entity.processor.constant;

import com.fincity.saas.commons.security.util.SecurityContextUtil;
import java.util.Collection;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.GrantedAuthority;

@UtilityClass
public class BusinessPartnerConstant {

    public static final String BP_MANAGER_ROLE = "Authorities.ROLE_Bp_Manager";

    public static final String BP_MANAGER = "bpManager";

    public static final String OWNER_ROLE = "Authorities.ROLE_Owner";

    public static final String OWNER = "owner";

    public static boolean isBpManager(Collection<? extends GrantedAuthority> collection) {
        return SecurityContextUtil.hasAuthority(BP_MANAGER_ROLE, collection)
                || SecurityContextUtil.hasAuthority(OWNER_ROLE, collection);
    }
}
