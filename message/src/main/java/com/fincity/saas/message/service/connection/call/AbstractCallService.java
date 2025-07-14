package com.fincity.saas.message.service.connection.call;

import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractCallService {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected CoreMessageResourceService msgService;

    protected String validatePhoneNumber(String phoneNumber) {
        // Basic validation - ensure the number starts with a + for international format
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "";
        }
        
        // If the number doesn't start with +, add it
        if (!phoneNumber.startsWith("+")) {
            return "+" + phoneNumber;
        }
        
        return phoneNumber;
    }
}
