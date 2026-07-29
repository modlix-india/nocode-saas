package com.modlix.saas.adzump.dao;

import static com.modlix.saas.adzump.jooq.tables.AdzumpAsset.ADZUMP_ASSET;

import java.util.Map;

import org.jooq.Record;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.jooq.tables.records.AdzumpAssetRecord;
import com.modlix.saas.adzump.model.asset.Asset;
import com.modlix.saas.adzump.model.asset.PlatformAssetId;

/**
 * DAO for {@code adzump_asset}. Extends the shared {@code AbstractAdzumpJsonDAO} so the two JSON
 * columns the plain JOOQ record&lt;-&gt;POJO mapper cannot translate are funnelled through the
 * custom-column hooks: {@code attributes} (free-form vision classification, mapped to a
 * {@link JsonNode}) and {@code platform_ids} (the per-platform registration cache, mapped to a
 * {@code Map<Platform, PlatformAssetId>}). Every other column ({@code id}, {@code client_code},
 * {@code kind}, {@code file_key}, {@code url}, audit) is the plain JOOQ mapping.
 */
@Service
public class AssetDao extends AbstractAdzumpJsonDAO<AdzumpAssetRecord, Asset> {

    private static final TypeReference<Map<Platform, PlatformAssetId>> PLATFORM_IDS_TYPE = new TypeReference<>() {
    };

    public AssetDao() {
        super(Asset.class, ADZUMP_ASSET, ADZUMP_ASSET.ID);
    }

    @Override
    protected void readCustomColumns(Record rec, Asset pojo) {
        pojo.setAttributes(fromJson(getJson(rec, ADZUMP_ASSET.ATTRIBUTES), JsonNode.class));
        pojo.setPlatformIds(fromJson(getJson(rec, ADZUMP_ASSET.PLATFORM_IDS), PLATFORM_IDS_TYPE));
    }

    @Override
    protected void writeCustomColumns(Asset pojo, AdzumpAssetRecord rec) {
        rec.set(ADZUMP_ASSET.ATTRIBUTES, toJson(pojo.getAttributes()));
        rec.set(ADZUMP_ASSET.PLATFORM_IDS, toJson(pojo.getPlatformIds()));
    }
}
