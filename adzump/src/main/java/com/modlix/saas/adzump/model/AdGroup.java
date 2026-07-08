package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.modlix.saas.adzump.enums.Platform;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AdGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = -8172635409182736450L;

    private String id;
    private String name;
    private Platform platform;
    private Targeting targeting;
    private Money budget;
    private Bid bid;
    private List<Ad> ads;
}
