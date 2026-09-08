package com.fincity.saas.entity.processor.analytics.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;


@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CampaignTrendResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<StageNode> stageTree;
    private List<CampaignTrendRow> rows;
}
