package com.fincity.saas.entity.processor.dto;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.EnumSchemaUtil;
import com.fincity.saas.entity.processor.model.request.ProductCommRequest;
import com.fincity.saas.entity.processor.oserver.core.document.Connection;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.util.PhoneUtil;
import com.google.gson.JsonPrimitive;
import java.io.Serial;
import java.util.Map;
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
public class ProductComm extends BaseProcessorDto<ProductComm> {

    @Serial
    private static final long serialVersionUID = 8028699089699178352L;

    private String connectionName;
    private String connectionType;
    private String connectionSubType;
    private ULong productId;
    private Integer dialCode = PhoneUtil.getDefaultCallingCode();
    private String phoneNumber;
    private String email;
    private boolean isDefault = false;
    private String source;
    private String subSource;

    public ProductComm() {
        super();
    }

    public ProductComm(ProductComm productComm) {
        super(productComm);
        this.connectionName = productComm.connectionName;
        this.connectionType = productComm.connectionType;
        this.connectionSubType = productComm.connectionSubType;
        this.productId = productComm.productId;
        this.dialCode = productComm.dialCode;
        this.phoneNumber = productComm.phoneNumber;
        this.email = productComm.email;
        this.isDefault = productComm.isDefault;
        this.source = productComm.source;
        this.subSource = productComm.subSource;
    }

    public static ProductComm of(ProductCommRequest productCommRequest, ULong productId, Connection connection) {
        ProductComm productComm = new ProductComm()
                .setConnectionName(connection.getName())
                .setConnectionType(connection.getConnectionType().name())
                .setConnectionSubType(connection.getConnectionSubType().name())
                .setProductId(productId)
                .setName(productCommRequest.getName());

        // App-level (no product) default will always be true
        if (productId == null) {
            productComm.setDefault(true);
            productComm.setSource(null).setSubSource(null);
        } else {
            productComm.setDefault(productCommRequest.isDefault());
            if (Boolean.FALSE.equals(productCommRequest.isDefault()))
                productComm.setSource(productCommRequest.getSource()).setSubSource(productCommRequest.getSubSource());
        }

        return switch (connection.getConnectionType()) {
            case TEXT, CALL ->
                productComm
                        .setDialCode(productCommRequest.getPhoneNumber().getCountryCode())
                        .setPhoneNumber(productCommRequest.getPhoneNumber().getNumber());
            case MAIL -> productComm.setEmail(productCommRequest.getEmail().getAddress());
            default -> productComm;
        };
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_COMM;
    }

    @Override
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();
        props.put(
                Fields.connectionName,
                Schema.ofString(Fields.connectionName).setMinLength(1).setMaxLength(255));
        props.put(
                Fields.connectionType,
                Schema.ofString(Fields.connectionType).setEnums(EnumSchemaUtil.getSchemaEnums(ConnectionType.class)));
        props.put(
                Fields.connectionSubType,
                Schema.ofString(Fields.connectionSubType)
                        .setEnums(EnumSchemaUtil.getSchemaEnums(ConnectionSubType.class)));
        props.put(Fields.productId, Schema.ofLong(Fields.productId).setMinimum(1));
        props.put(
                Fields.dialCode,
                Schema.ofInteger(Fields.dialCode)
                        .setMinimum(1)
                        .setDefaultValue(new JsonPrimitive(PhoneUtil.getDefaultCallingCode())));
        props.put(Fields.phoneNumber, Schema.ofString(Fields.phoneNumber));
        props.put(Fields.email, Schema.ofString(Fields.email).setFormat(StringFormat.EMAIL));
        props.put(Fields.isDefault, Schema.ofBoolean(Fields.isDefault).setDefaultValue(new JsonPrimitive(false)));
        props.put(Fields.source, Schema.ofString(Fields.source));
        props.put(Fields.subSource, Schema.ofString(Fields.subSource));

        schema.setProperties(props);
        return schema;
    }
}
