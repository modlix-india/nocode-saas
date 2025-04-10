package com.fincity.saas.core.controller;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.repository.StorageRepository;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/core/storages")
public class StorageController extends AbstractOverridableDataController<Storage, StorageRepository, StorageService> {

}
