package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Designation extends AbstractUpdatableDTO<ULong, ULong> {

    private static final long serialVersionUID = 1L;

    private ULong clientId;
    private String name;
    private String description;
    private ULong parentDesignationId;
    private ULong departmentId;
    private ULong nextDesignationId;
}
