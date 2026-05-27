package com.fincity.saas.entity.processor.analytics.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Tree response paired with the stage column tree that the UI uses to render
 * collapsible-dynamic stage columns. Stage IDs in {@link CampaignReport#getStageCells()}
 * key into {@link #stageTree}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CampaignTreeResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<StageNode> stageTree;
    private List<CampaignReport> rows;
}
