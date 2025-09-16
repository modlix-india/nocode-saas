package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.model.request.ProductCommRequest;
import com.fincity.saas.entity.processor.oserver.core.document.Connection;
import com.fincity.saas.entity.processor.util.PhoneUtil;
import java.io.Serial;
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
public class ProductComm extends BaseUpdatableDto<ProductComm> {

    @Serial
    private static final long serialVersionUID = 8028699089699178352L;

    private String connectionName;
    private String connectionType;
    private ULong productId;
    private Integer dialCode = PhoneUtil.getDefaultCallingCode();
    private String phoneNumber;
    private String email;
    private boolean isDefault = false;

    public ProductComm() {
        super();
    }

    public ProductComm(ProductComm productComm) {
        super(productComm);
        this.connectionName = productComm.connectionName;
        this.connectionType = productComm.connectionType;
        this.productId = productComm.productId;
        this.dialCode = productComm.dialCode;
        this.phoneNumber = productComm.phoneNumber;
        this.email = productComm.email;
        this.isDefault = productComm.isDefault;
    }

    public static ProductComm of(ProductCommRequest productCommRequest, ULong productId, Connection connection) {
        ProductComm productComm = new ProductComm()
                .setConnectionName(connection.getName())
                .setConnectionType(connection.getConnectionType().name())
                .setProductId(productId)
                .setDefault(productCommRequest.getIsDefault())
                .setName(productCommRequest.getName());

        return switch (connection.getConnectionType()) {
            case TEXT, CALL ->
                productComm
                        .setDialCode(productCommRequest.getPhoneNumber().getCountryCode())
                        .setPhoneNumber(productCommRequest.getPhoneNumber().getNumber());
            case MAIL -> productComm.setEmail(productCommRequest.getEmail().getAddress());
            default -> productComm;
        };
    }
}
