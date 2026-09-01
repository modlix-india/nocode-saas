package com.fincity.saas.ui.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractDraftService;

/**
 * Concrete draft store for the ui service, mirroring how VersionService
 * concretises AbstractVersionService.
 */
@Service
public class DraftService extends AbstractDraftService {
}
