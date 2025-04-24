package com.fincity.saas.entity.processor.service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import org.springframework.stereotype.Service;

@Service
public class ProcessorMessageResourceService extends AbstractMongoMessageResourceService {

    public static final String LOGIN_REQUIRED = "login_required";
    public static final String INVALID_CHILD_FOR_PARENT = "invalid_child_for_parent";
    public static final String INVALID_USER_FOR_CLIENT = "invalid_user_for_client";
    public static final String PRODUCT_IDENTITY_MISSING = "product_identity_missing";
    public static final String PRODUCT_IDENTITY_WRONG = "product_identity_wrong";
}
