package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Bid implements Serializable {

    @Serial
    private static final long serialVersionUID = 5610293847561029384L;

    private String strategy;
    private Money targetCpa;
}
