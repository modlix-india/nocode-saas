package com.fincity.security.dto.invoicesnpayments;

import java.io.Serial;
import java.math.BigDecimal;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InvoiceItem extends AbstractUpdatableDTO<ULong, ULong> {
    @Serial
    private static final long serialVersionUID = 1L;

    private ULong invoiceId;
    private String itemName;
    private String itemDescription;
    private BigDecimal itemAmount;
    private BigDecimal itemTax1;
    private BigDecimal itemTax2;
    private BigDecimal itemTax3;
    private BigDecimal itemTax4;
    private BigDecimal itemTax5;

    private BigDecimal getItemTotalAmount() {
        return this.itemAmount.add(this.itemTax1).add(this.itemTax2).add(this.itemTax3).add(this.itemTax4)
                .add(this.itemTax5);
    }

    public BigDecimal getItemTotalTax() {
        return this.itemTax1.add(this.itemTax2).add(this.itemTax3).add(this.itemTax4).add(this.itemTax5);
    }
}
