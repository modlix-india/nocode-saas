package com.modlix.saas.adzump;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@ComponentScan(basePackages = "com.modlix")
@EnableFeignClients(basePackages = "com.modlix")
@EnableWebSecurity
@EnableMethodSecurity
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@SpringBootApplication(exclude = {WebFluxAutoConfiguration.class})
public class AdzumpApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdzumpApplication.class, args);
    }
}
