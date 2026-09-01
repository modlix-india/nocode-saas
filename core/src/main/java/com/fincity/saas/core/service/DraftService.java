package com.fincity.saas.core.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractDraftService;

/**
 * Concrete draft store for the core service, mirroring how VersionService
 * concretises AbstractVersionService.
 */
@Service
public class DraftService extends AbstractDraftService {
}
