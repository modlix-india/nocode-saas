package com.fincity.saas.commons.service;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CacheObject implements Serializable {

    private static final long serialVersionUID = -461700839265136404L;

    private Object object; // NOSONAR
}
