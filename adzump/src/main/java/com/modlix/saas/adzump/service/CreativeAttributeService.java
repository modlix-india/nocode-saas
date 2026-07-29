package com.modlix.saas.adzump.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.dao.CreativeAttributeDao;
import com.modlix.saas.adzump.dto.CreativeAttributeRow;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.model.condition.ComplexCondition;
import com.modlix.saas.commons2.model.condition.ComplexConditionOperator;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

@Service
public class CreativeAttributeService {

    private static final String CLIENT_CODE = "clientCode";
    private static final String CREATIVE_ID = "creativeId";

    private final CreativeAttributeDao dao;
    private final AdzumpMessageResourceService msgService;

    public CreativeAttributeService(CreativeAttributeDao dao, AdzumpMessageResourceService msgService) {

        this.dao = dao;
        this.msgService = msgService;
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')")
    public CreativeAttributeRow create(CreativeAttributeRow row) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        if (row.getCreativeId() == null || row.getCreativeId().isBlank())
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "creativeId");

        if (row.getAxis() == null || row.getAxis().isBlank())
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "axis");

        if (row.getValue() == null || row.getValue().isBlank())
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "value");

        // Client scope ALWAYS comes from the security context, never from the request body.
        row.setClientCode(ca.getLoggedInFromClientCode());

        return this.dao.create(row);
    }

    // No @PreAuthorize on reads; tenant-scoped by the caller's clientCode from the
    // security context, so cross-tenant rows can never be resolved.
    public List<CreativeAttributeRow> byCreative(String creativeId) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        List<AbstractCondition> conditions = List.of(
                FilterCondition.make(CLIENT_CODE, ca.getLoggedInFromClientCode()),
                FilterCondition.make(CREATIVE_ID, creativeId));

        return this.dao.readAll(
                new ComplexCondition().setConditions(conditions).setOperator(ComplexConditionOperator.AND));
    }
}
