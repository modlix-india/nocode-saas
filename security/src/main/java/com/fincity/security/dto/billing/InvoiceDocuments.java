package com.fincity.security.dto.billing;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Download links for an invoice's generated PDFs. Each is a relative, time-limited
 * secured-key URL ({@code api/files/secured/downloadFileByKey/{key}}) minted only
 * after the invoice party-guard has run. A field is {@code null} when the caller is
 * not a party or the PDF was never generated.
 */
@Data
@Accessors(chain = true)
public class InvoiceDocuments {

    private String invoiceUrl;
    private String receiptUrl;
}
