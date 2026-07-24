package com.fincity.security.controller.billing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.model.billing.CheckoutOrderResult;
import com.fincity.security.model.billing.PurchaseRequest;
import com.fincity.security.model.billing.PurchaseResult;
import com.fincity.security.model.billing.QuoteResult;
import com.fincity.security.model.billing.RepayRequest;
import com.fincity.security.service.billing.BundlePurchaseService;
import com.fincity.security.service.billing.RazorpayPaymentService;

import reactor.core.publisher.Mono;

/**
 * Token-bundle purchase: start a payment (authenticated) and the Razorpay webhook
 * (public, signature-verified inside the service).
 */
@RestController
@RequestMapping("api/security/billing")
public class BundlePurchaseController {

    private final BundlePurchaseService purchaseService;
    private final RazorpayPaymentService razorpayService;

    public BundlePurchaseController(BundlePurchaseService purchaseService, RazorpayPaymentService razorpayService) {
        this.purchaseService = purchaseService;
        this.razorpayService = razorpayService;
    }

    @PostMapping("/purchase")
    public Mono<ResponseEntity<PurchaseResult>> purchase(@RequestBody PurchaseRequest request) {
        return this.purchaseService.purchase(request.bundleId(), request.tokens(), request.clientId())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/purchase/order")
    public Mono<ResponseEntity<CheckoutOrderResult>> purchaseOrder(@RequestBody PurchaseRequest request) {
        return this.purchaseService.purchaseWithOrder(request.bundleId(), request.tokens(), request.clientId())
                .map(ResponseEntity::ok);
    }

    /** Compute-only price breakup for the order-summary popup (nothing persisted). */
    @PostMapping("/quote")
    public Mono<ResponseEntity<QuoteResult>> quote(@RequestBody PurchaseRequest request) {
        return this.purchaseService.quote(request.bundleId(), request.tokens())
                .map(ResponseEntity::ok);
    }

    /** Start a fresh Razorpay Order for an existing PENDING/FAILED invoice the caller owns. */
    @PostMapping("/repay")
    public Mono<ResponseEntity<CheckoutOrderResult>> repay(@RequestBody RepayRequest request) {
        return this.purchaseService.repay(request.invoiceId()).map(ResponseEntity::ok);
    }

    @PostMapping("/razorpay/webhook")
    public Mono<ResponseEntity<String>> webhook(@RequestBody String body,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        return this.razorpayService.handleWebhook(body, signature).thenReturn(ResponseEntity.ok("OK"));
    }
}
