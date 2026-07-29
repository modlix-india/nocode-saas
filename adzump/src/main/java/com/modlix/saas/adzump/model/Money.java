package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class Money implements Serializable {

    @Serial
    private static final long serialVersionUID = 4125982716349871231L;

    private BigDecimal amount;
    private String currency;
}
