package com.fincity.saas.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.web.reactive.config.EnableWebFlux;

import reactivefeign.spring.config.EnableReactiveFeignClients;

@EnableEurekaClient
@EnableReactiveFeignClients
@EnableWebFlux
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@EnableCaching
@ComponentScan(basePackages = "com.fincity")
@EnableAspectJAutoProxy(proxyTargetClass = true)
@SpringBootApplication
public class UiApplication {

	public static void main(String[] args) {
		SpringApplication.run(UiApplication.class, args);
	}

}
