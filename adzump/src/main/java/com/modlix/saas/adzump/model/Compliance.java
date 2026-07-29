package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.modlix.saas.adzump.enums.SpecialAdCategory;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Compliance implements Serializable {

    @Serial
    private static final long serialVersionUID = 6574839201847561023L;

    private SpecialAdCategory specialAdCategory;
    private List<String> disclaimers;
}
