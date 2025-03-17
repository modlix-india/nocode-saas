package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProfileArrangement extends AbstractDTO<ULong, ULong> {

    private static final long serialVersionUID = 1L;

    private ULong profileId;
    private ULong clientId;
    private ULong roleId;
    private String name;
    private String shortName;
    private String description;
    private boolean assignable;
    private ULong parentArrangementId;
    private int order;
}