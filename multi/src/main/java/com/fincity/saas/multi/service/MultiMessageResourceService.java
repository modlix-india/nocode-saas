package com.fincity.saas.multi.service;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;

@Service
public class MultiMessageResourceService extends AbstractMessageService {

	public static final String FORBIDDEN_CREATE = "forbidden_create";

	public MultiMessageResourceService() {

		super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH)));
	}

}
