package com.fincity.saas.entity.processor.constant;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;

import com.fincity.saas.commons.security.util.SecurityContextUtil;

import lombok.experimental.UtilityClass;

@UtilityClass
public class BusinessPartnerConstant {

    public static final String BP_MANAGER_ROLE = "Authorities.ROLE_Partner_Manager";

    public static final String OWNER_ROLE = "Authorities.ROLE_Owner";

    public static final String CLIENT_LEVEL_TYPE_BP = "CUSTOMER";

    public static boolean isBpManager(Collection<? extends GrantedAuthority> collection) {
        return SecurityContextUtil.hasAuthority(BP_MANAGER_ROLE, collection) || SecurityContextUtil.hasAuthority(OWNER_ROLE, collection);
    }
}
