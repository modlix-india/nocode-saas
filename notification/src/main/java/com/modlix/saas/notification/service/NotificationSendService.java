package com.modlix.saas.notification.service;

import com.modlix.saas.commons2.mq.notifications.NotificationQueObject;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.util.LogUtil;
import com.modlix.saas.notification.feign.IFeignCoreService;
import org.springframework.stereotype.Service;

@Service
public class NotificationSendService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NotificationSendService.class);

    private final IFeignCoreService coreService;

    private final IFeignSecurityService securityService;

    public NotificationSendService(IFeignSecurityService securityService, IFeignCoreService coreService) {

        this.securityService = securityService;
        this.coreService = coreService;
    }

    public void sendNotification(NotificationQueObject qob) {

//        this.coreService.
//                LogUtil.info(logger, "Sending notification: {}", qob);
    }
}
