package com.fincity.saas.commons.mongo.document;

import java.io.Serial;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Unpublished work on an overridable object.
 *
 * Held in its own collection rather than as a field on the object itself. A
 * `draft` map on AbstractOverridableDTO would land on ListResultObject too, which
 * extends the same base, and readPageFilterLRO reads whole documents into it with
 * no projection, so every list call would materialize every object's full draft
 * payload. Those results are cached, and redis.codec defaults to `object`
 * (RedisObjectCodec, Java serialization), so @JsonIgnore would not have kept them
 * out of Redis either. Separating the collection avoids all of that, and keeps the
 * DTO hierarchy in `commons` untouched for a feature only ui and core want.
 *
 * `content` holds the entity exactly as the caller sent it, NOT the
 * post-extractOverride delta, so publish can run the whole normal update pipeline
 * over it rather than reimplementing the diffing.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'objectAppCode': 1, 'objectType': 1, 'objectName': 1, 'clientCode': 1}",
        name = "draftFilteringIndex", unique = true)
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Draft extends AbstractUpdatableDTO<String, String> {

    @Serial
    private static final long serialVersionUID = 5236618453911903561L;

    /** Uppercased object name, matching Version.objectType: "PAGE", "STORAGE", ... */
    private String objectType;

    private String objectAppCode;
    private String objectName;
    private String clientCode;

    /**
     * The live document this draft belongs to. Always set: creation is never
     * drafted, so every draft has a real object behind it.
     */
    private String objectId;

    private Map<String, Object> content; // NOSONAR

    /**
     * The live document's version at the moment the draft was taken. Publish
     * restores it before calling update(), so the existing optimistic-lock check
     * rejects a publish whose base has moved on underneath it.
     */
    private int baseVersion;

    /**
     * The DRAFT's own version, incremented on every save. Distinct from
     * baseVersion, which is the LIVE document's version and is frozen.
     *
     * There is one draft row per (app, type, name, clientCode), so it belongs to a
     * CLIENT and not to a user: two people editing the same object share it. Before
     * this field the second save simply replaced the first's content through the
     * upsert, and neither person was told. baseVersion cannot detect that, because
     * both people read the same live document and so send the same number; only a
     * counter on the draft itself moves when someone else saves.
     */
    private int version = 1;

    private String message;
}
