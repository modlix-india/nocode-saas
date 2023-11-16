package com.fincity.saas.schedular.service;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;

@Service
public class SchedularMessageResourceService extends AbstractMessageService {

	public SchedularMessageResourceService() {

		super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH)));
	}

}
