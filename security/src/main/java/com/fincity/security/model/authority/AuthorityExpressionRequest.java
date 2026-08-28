package com.fincity.security.model.authority;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuthorityExpressionRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -8871104457720031455L;

    private String expression;
}
