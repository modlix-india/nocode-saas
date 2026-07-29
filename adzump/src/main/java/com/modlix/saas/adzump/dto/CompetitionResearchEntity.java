package com.modlix.saas.adzump.dto;

import java.io.Serial;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.modlix.saas.adzump.model.competition.CompetitionResearchBody;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * The persistence row for {@code adzump_competition_research} (J19). The flat columns carry the
 * addressing + time key; the JSON {@code body} carries the proxy-ranked findings
 * ({@link CompetitionResearchBody}). Same column/JSON split as
 * {@link PerformanceSnapshotEntity} — the DAO maps the plain columns by name and the body via the JSON
 * hook.
 *
 * <p>Tenant-private: {@link #clientCode} pins the row to one client; every read is scoped to the
 * resolved effective client (J19 §5.5).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class CompetitionResearchEntity extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = -4610293847610293841L;

    private String clientCode;
    private String productId;
    private String vertical;
    private LocalDateTime generatedAt;
    private CompetitionResearchBody body;
}
