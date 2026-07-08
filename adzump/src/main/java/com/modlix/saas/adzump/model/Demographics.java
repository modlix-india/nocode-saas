package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Demographics implements Serializable {

    @Serial
    private static final long serialVersionUID = 7465102938475610293L;

    private Integer ageMin;
    private Integer ageMax;
    private List<String> genders;
}
