package com.fincity.saas.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.repository.StorageRepository;
import com.fincity.saas.core.service.StorageService;

@RestController
@RequestMapping("api/core/stores")
public class StoreController extends AbstractOverridableDataController<Storage, StorageRepository, StorageService> {

}
