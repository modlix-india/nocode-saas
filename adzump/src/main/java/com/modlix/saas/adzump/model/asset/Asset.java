package com.modlix.saas.adzump.model.asset;

import java.io.Serial;
import java.util.Map;

import org.jooq.types.ULong;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.jooq.enums.AdzumpAssetKind;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * J16 asset entity — one row of {@code adzump_asset}. It is the tenant-private record of a creative
 * medium (image / video / logo) a campaign can use, and the bridge between Modlix file storage and the
 * per-platform ad-asset libraries.
 *
 * <p>The {@code fileKey}/{@code url} are the Modlix files-service reference (the <b>source of truth</b>
 * for the bytes; adzump stores no blobs). {@code attributes} carries A4/vision classification (theme,
 * scene, dominant colour, logo/hero/amenity/floor-plan …). {@code platformIds} is the cache of
 * per-platform registration results ({@link PlatformAssetId}), serialized to the {@code platform_ids}
 * JSON column; a creative references the platform id there, never the raw URL, so J16 registers an
 * asset once per platform and never re-uploads a {@code READY} one.
 *
 * <p>The JSON columns ({@code attributes}, {@code platform_ids}) are mapped by {@code AssetDao} through
 * the {@code AbstractAdzumpJsonDAO} custom-column hooks; every other column is the plain JOOQ mapping.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class Asset extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 7301948562039184756L;

    private String clientCode;
    private AdzumpAssetKind kind;
    private String fileKey;
    private String url;
    private JsonNode attributes;
    private Map<Platform, PlatformAssetId> platformIds;
}
