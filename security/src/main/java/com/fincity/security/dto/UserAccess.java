package com.fincity.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserAccess implements Serializable {

    @Serial
    private static final long serialVersionUID = -2830351092181121428L;

    private boolean app;
    private boolean owner;
    private boolean clientAccess;
    private String appOneTimeToken;
    private String appURL;
}
