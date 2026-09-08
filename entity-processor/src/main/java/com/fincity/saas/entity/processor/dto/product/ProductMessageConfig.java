package com.fincity.saas.entity.processor.dto.product;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.MessageChannelType;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
@IgnoreGeneration
public class ProductMessageConfig extends BaseUpdatableDto<ProductMessageConfig> {

    @Serial
    private static final long serialVersionUID = 3294737603455853726L;

    private ULong productId;
    private ULong stageId;
    private ULong statusId;

    private MessageChannelType channel;
    private Integer order;

    /**
     * A message from the library, or null when this rule carries its own text in {@link
     * #bodyVariants}.
     *
     * <p>Nullable since the pivot. It used to name a Meta-approved template and was mandatory,
     * because nothing else could legally be sent. On the linked-device protocol any text can go at
     * any time, so referencing the library is a convenience rather than a requirement, and a rule
     * with one throwaway line does not need a library entry to exist first.
     */
    private ULong messageTemplateId;

    /**
     * Interchangeable bodies for this rule, used when it does not reference the library.
     *
     * <p>Several phrasings rather than one, for the reason the library has them: a rule sends the
     * same message to every matching lead, and identical text to more than roughly fifteen
     * recipients an hour is a documented trigger for the enforcement this whole design exists to
     * avoid. One variant is allowed and warned about; none plus no template is a rule that cannot
     * send.
     */
    private List<String> bodyVariants = new ArrayList<>();

    /**
     * The asset this config sends alongside its body.
     *
     * <p>Null for a plain text config. Several configs on the same stage, ordered by {@link #order},
     * are what makes up a welcome packet.
     */
    private FileDetail assetFileDetail;

    /** Caption sent with the asset. */
    private String caption;

    public ProductMessageConfig() {
        super();
    }

    public ProductMessageConfig(ProductMessageConfig other) {
        super(other);
        this.productId = other.productId;
        this.stageId = other.stageId;
        this.statusId = other.statusId;
        this.channel = other.channel;
        this.order = other.order;
        this.messageTemplateId = other.messageTemplateId;
        this.bodyVariants = other.bodyVariants == null ? new ArrayList<>() : new ArrayList<>(other.bodyVariants);
        this.assetFileDetail = other.assetFileDetail;
        this.caption = other.caption;
    }

    /**
     * Picks this rule's own phrasing for a given recipient.
     *
     * <p>Rotated by a caller-supplied index rather than at random, so the choice is reproducible
     * when reading back what was actually sent to a lead months later.
     */
    public String variantFor(long rotation) {
        if (this.bodyVariants == null || this.bodyVariants.isEmpty()) return null;
        return this.bodyVariants.get((int) Math.floorMod(rotation, this.bodyVariants.size()));
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_MESSAGE_CONFIGS;
    }
}
