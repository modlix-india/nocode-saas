package com.fincity.security.dto.billing;

import java.io.Serial;
import java.math.BigDecimal;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityActionCatalogDefaultActionClass;
import com.fincity.security.jooq.enums.SecurityActionCatalogStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Platform-wide catalog of meterable actions. The default class and unit cost
 * are overridable per app via {@link AppActionCost}.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ActionCatalog extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private String actionKey;
    private String name;
    private String description;
    private SecurityActionCatalogDefaultActionClass defaultActionClass;
    private BigDecimal defaultUnitCost;
    private String unitName;
    private SecurityActionCatalogStatus status;
}
