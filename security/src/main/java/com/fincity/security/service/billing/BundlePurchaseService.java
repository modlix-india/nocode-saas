package com.fincity.security.service.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.billing.InvoiceDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.billing.AppBillingBundle;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.dto.billing.Invoice;
import com.fincity.security.jooq.enums.SecurityAppBillingBundleBundleType;
import com.fincity.security.jooq.enums.SecurityInvoiceStatus;
import com.fincity.security.model.billing.CheckoutOrderResult;
import com.fincity.security.model.billing.PurchaseResult;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Starts a token-bundle purchase: prices the bundle (FIXED tier, or CUSTOM at
 * price-per-token), adds config GST on top, snapshots seller (C) and buyer (M)
 * onto a PENDING invoice, and creates the Razorpay payment link. The webhook
 * (not this flow) is what credits the wallet.
 */
@Service
public class BundlePurchaseService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AppBillingBundleService bundleService;
    private final AppBillingConfigService configService;
    private final InvoiceService invoiceService;
    private final InvoiceDAO invoiceDAO;
    private final RazorpayPaymentService razorpayService;
    private final ClientService clientService;
    private final SecurityMessageResourceService messageResourceService;

    public BundlePurchaseService(AppBillingBundleService bundleService, AppBillingConfigService configService,
            InvoiceService invoiceService, InvoiceDAO invoiceDAO, RazorpayPaymentService razorpayService,
            ClientService clientService, SecurityMessageResourceService messageResourceService) {
        this.bundleService = bundleService;
        this.configService = configService;
        this.invoiceService = invoiceService;
        this.invoiceDAO = invoiceDAO;
        this.razorpayService = razorpayService;
        this.clientService = clientService;
        this.messageResourceService = messageResourceService;
    }

    @PreAuthorize("hasAnyAuthority('Authorities.ROLE_Owner', 'Authorities.Payment_CREATE')")
    public Mono<PurchaseResult> purchase(ULong bundleId, BigDecimal tokensRequested, ULong buyerClientId) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> this.resolveBuyer(ca, buyerClientId),
                (ca, buyer) -> this.bundleService.readForPurchase(bundleId)
                        .switchIfEmpty(this.badRequest("bundle")),
                (ca, buyer, bundle) -> this.configService.readInternal(bundle.getBillingConfigId())
                        .switchIfEmpty(this.badRequest("billing config")),
                (ca, buyer, bundle, config) -> this.buildInvoice(ca, buyer, bundle, config, tokensRequested),
                (ca, buyer, bundle, config, invoice) -> this.invoiceService.create(invoice),
                (ca, buyer, bundle, config, invoice, saved) -> this.razorpayService.initialize(saved, config)
                        .map(url -> new PurchaseResult(saved.getId(), saved.getInvoiceNumber(),
                                saved.getTotalAmount(), saved.getCurrency(), url)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BundlePurchaseService.purchase"));
    }

    /**
     * Same pricing/invoice flow as {@link #purchase}, but starts a Razorpay Order for the
     * in-page Checkout.js modal instead of a hosted payment link. Returns everything the
     * browser needs to open the modal. The webhook (not this flow) credits the wallet.
     */
    @PreAuthorize("hasAnyAuthority('Authorities.ROLE_Owner', 'Authorities.Payment_CREATE')")
    public Mono<CheckoutOrderResult> purchaseWithOrder(ULong bundleId, BigDecimal tokensRequested,
            ULong buyerClientId) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> this.resolveBuyer(ca, buyerClientId),
                (ca, buyer) -> this.bundleService.readForPurchase(bundleId)
                        .switchIfEmpty(this.badRequest("bundle")),
                (ca, buyer, bundle) -> this.configService.readInternal(bundle.getBillingConfigId())
                        .switchIfEmpty(this.badRequest("billing config")),
                (ca, buyer, bundle, config) -> this.buildInvoice(ca, buyer, bundle, config, tokensRequested),
                (ca, buyer, bundle, config, invoice) -> this.invoiceService.create(invoice),
                (ca, buyer, bundle, config, invoice, saved) -> this.razorpayService.createOrder(saved, config,
                        prefillName(ca, buyer), ca.getUser().getEmailId(), ca.getUser().getPhoneNumber()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BundlePurchaseService.purchaseWithOrder"));
    }

    /** Prefer the user's full name for the Razorpay prefill; fall back to the buyer client name. */
    private static String prefillName(ContextAuthentication ca, Client buyer) {
        String first = ca.getUser() == null ? null : ca.getUser().getFirstName();
        String last = ca.getUser() == null ? null : ca.getUser().getLastName();
        String full = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        if (!full.isEmpty())
            return full;
        return buyer == null ? null : buyer.getName();
    }

    private Mono<Client> resolveBuyer(ContextAuthentication ca, ULong buyerClientId) {
        ULong own = ULong.valueOf(ca.getUser().getClientId());
        if (buyerClientId == null || buyerClientId.equals(own))
            return this.clientService.getClientInfoById(own);
        return this.clientService.isUserClientManageClient(ca, buyerClientId)
                .filter(BooleanUtil::safeValueOf)
                .switchIfEmpty(this.forbidden("buy for this client"))
                .then(this.clientService.getClientInfoById(buyerClientId));
    }

    private Mono<Invoice> buildInvoice(ContextAuthentication ca, Client buyer, AppBillingBundle bundle,
            AppBillingConfig config, BigDecimal tokensRequested) {

        BigDecimal tokens;
        BigDecimal base;
        if (bundle.getBundleType() == SecurityAppBillingBundleBundleType.CUSTOM) {
            if (tokensRequested == null || tokensRequested.signum() <= 0)
                return this.badRequest("tokens");
            if (bundle.getMinTokens() != null && tokensRequested.compareTo(bundle.getMinTokens()) < 0)
                return this.badRequest("tokens (below minimum)");
            if (bundle.getMaxTokens() != null && tokensRequested.compareTo(bundle.getMaxTokens()) > 0)
                return this.badRequest("tokens (above maximum)");
            tokens = tokensRequested;
            base = tokensRequested.multiply(bundle.getPricePerToken());
        } else {
            tokens = bundle.getTokens();
            base = bundle.getPrice();
        }

        BigDecimal gstPct = config.getGstPercentage() == null ? BigDecimal.ZERO : config.getGstPercentage();
        BigDecimal gstAmount = base.multiply(gstPct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal total = base.add(gstAmount);
        String currency = bundle.getCurrency() == null ? "INR" : bundle.getCurrency();

        return this.invoiceDAO.nextInvoiceNumber(config.getClientId(), finYear(LocalDate.now()))
                .map(invoiceNumber -> new Invoice()
                        .setInvoiceNumber(invoiceNumber)
                        .setInvoiceDate(LocalDateTime.now())
                        .setStatus(SecurityInvoiceStatus.PENDING)
                        .setSellerClientId(config.getClientId())
                        .setSellerLegalName(config.getSellerLegalName())
                        .setSellerGstin(config.getSellerGstin())
                        .setSellerAddress(config.getSellerAddress())
                        .setClientId(buyer.getId())
                        .setBuyerLegalName(buyer.getName())
                        .setAppId(config.getAppId())
                        .setBundleId(bundle.getId())
                        .setBundleLabel(bundle.getLabel())
                        .setTokensPurchased(tokens)
                        .setBaseAmount(base)
                        .setGstPercentage(gstPct)
                        .setGstAmount(gstAmount)
                        .setTotalAmount(total)
                        .setCurrency(currency));
    }

    /** Indian financial year (Apr-Mar) as {@code YYYY-YY}. */
    private static String finYear(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return startYear + "-" + String.format("%02d", (startYear + 1) % 100);
    }

    private <T> Mono<T> badRequest(String what) {
        return this.messageResourceService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                SecurityMessageResourceService.PARAMS_NOT_FOUND, what);
    }

    private <T> Mono<T> forbidden(String what) {
        return this.messageResourceService.throwMessage(
                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                SecurityMessageResourceService.FORBIDDEN_PERMISSION, what);
    }
}
