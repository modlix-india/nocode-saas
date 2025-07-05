package com.fincity.saas.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactivefeign.spring.config.EnableReactiveFeignClients;

@SpringBootApplication
@EnableFeignClients
@EnableReactiveFeignClients(basePackages = "com.fincity")
@EnableWebFlux
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity(order = Ordered.HIGHEST_PRECEDENCE)
@EnableCaching
@ComponentScan(basePackages = "com.fincity")
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
