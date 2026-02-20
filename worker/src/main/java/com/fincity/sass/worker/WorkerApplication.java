package com.fincity.sass.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@ComponentScan(basePackages = {"com.fincity", "com.modlix"})
@EnableFeignClients(basePackages = {"com.fincity", "com.modlix"})
@EnableWebSecurity
@EnableMethodSecurity
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableScheduling
@SpringBootApplication(exclude = {WebFluxAutoConfiguration.class})
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
