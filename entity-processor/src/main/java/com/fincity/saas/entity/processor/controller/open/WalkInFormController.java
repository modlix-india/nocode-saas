package com.fincity.saas.entity.processor.controller.open;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.response.WalkInFormResponse;
import com.fincity.saas.entity.processor.service.form.ProductWalkInFormService;

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
            @PathVariable(PATH_VARIABLE_ID) final Identity identity) {

        return this.productWalkInFormService
                .getWalkInFormResponse(appCode, clientCode, identity)
                .map(ResponseEntity::ok);
    }
}
