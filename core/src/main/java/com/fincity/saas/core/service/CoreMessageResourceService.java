package com.fincity.saas.core.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;

@Service
public class CoreMessageResourceService extends AbstractMongoMessageResourceService {

	public static final String STORAGE_NOT_FOUND = "storage_not_found";

	public static final String CONNECTION_DETAILS_MISSING = "connection_details_missing";

	public static final String FORBIDDEN_CREATE_STORAGE = "forbidden_create_storage";
}
