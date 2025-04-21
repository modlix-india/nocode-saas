package com.fincity.saas.entity.processor.service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import org.springframework.stereotype.Service;

@Service
public class ProcessorMessageResourceService extends AbstractMongoMessageResourceService {

	public static final String INVALID_USER_FOR_CLIENT = "invalid_user_for_client";
}
