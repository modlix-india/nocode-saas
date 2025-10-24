package com.fincity.saas.entity.processor.controller.open;

import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.request.form.WalkInFormTicketRequest;
import com.fincity.saas.entity.processor.model.response.ProcessorResponse;
import com.fincity.saas.entity.processor.model.response.WalkInFormResponse;
import com.fincity.saas.entity.processor.service.form.ProductWalkInFormService;
import java.math.BigInteger;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/open/forms")
public class WalkInFormController {

    public static final String PATH_VARIABLE_ID = "id";
    public static final String PATH_ID = "/{" + PATH_VARIABLE_ID + "}";

    public final ProductWalkInFormService productWalkInFormService;

    public WalkInFormController(ProductWalkInFormService productWalkInFormService) {
        this.productWalkInFormService = productWalkInFormService;
    }

    @GetMapping(PATH_ID)
    public Mono<ResponseEntity<WalkInFormResponse>> getWalkInformResponse(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @PathVariable(PATH_VARIABLE_ID) final Identity productId) {

        return this.productWalkInFormService
                .getWalkInFormResponse(appCode, clientCode, productId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @GetMapping(PATH_ID + "/product")
    public Mono<ResponseEntity<Product>> getWalkInProduct(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @PathVariable(PATH_VARIABLE_ID) final Identity productId) {

        return this.productWalkInFormService
                .getWalkInProduct(appCode, clientCode, productId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @GetMapping(PATH_ID + "/ticket")
    public Mono<ResponseEntity<Ticket>> getWalkInTicket(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @PathVariable(PATH_VARIABLE_ID) final Identity productId,
            @RequestParam(name = "phoneNumber") PhoneNumber phoneNumber) {

        return this.productWalkInFormService
                .getWalkInTicket(appCode, clientCode, productId, phoneNumber)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @GetMapping("/users")
    public Mono<ResponseEntity<List<IdAndValue<BigInteger, String>>>> getWalkInFromUsers(
            @RequestHeader("appCode") String appCode, @RequestHeader("clientCode") String clientCode) {
        return this.productWalkInFormService
                .getWalkInFromUsers(appCode, clientCode)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @PostMapping(PATH_ID)
    public Mono<ResponseEntity<ProcessorResponse>> createWalkInTicket(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @PathVariable(PATH_VARIABLE_ID) final Identity productId,
            @RequestBody WalkInFormTicketRequest ticketRequest) {

        return this.productWalkInFormService
                .createWalkInTicket(appCode, clientCode, productId, ticketRequest)
                .map(ResponseEntity::ok);
    }
}
