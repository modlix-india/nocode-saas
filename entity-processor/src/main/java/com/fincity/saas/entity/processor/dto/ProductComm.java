package com.fincity.saas.entity.processor.dto;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.util.DbSchema;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
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
    public void extendSchema(Schema schema) {

        super.extendSchema(schema);

        Map<String, Schema> props = schema.getProperties();

        props.put(Fields.connectionName, DbSchema.ofChar(Fields.connectionName, 255));
        props.put(Fields.connectionType, DbSchema.ofEnum(Fields.connectionType, ConnectionType.class));
        props.put(Fields.connectionSubType, DbSchema.ofEnum(Fields.connectionSubType, ConnectionSubType.class));
        props.put(Fields.productId, DbSchema.ofNumberId(Fields.productId));
        props.put(
                Ticket.Fields.dialCode,
                DbSchema.ofDialCode(Ticket.Fields.dialCode)
                        .setDefaultValue(new JsonPrimitive(PhoneUtil.getDefaultCallingCode())));
        props.put(Ticket.Fields.phoneNumber, DbSchema.ofPhoneNumber(Ticket.Fields.phoneNumber));
        props.put(Fields.email, DbSchema.ofEmail(Fields.email));
        props.put(Fields.isDefault, DbSchema.ofBooleanFalse(Fields.isDefault));
        props.put(Fields.source, DbSchema.ofChar(Fields.source, 32));
        props.put(Fields.subSource, DbSchema.ofCharNull(Fields.subSource, 32));

        schema.setProperties(props);
    }
}
