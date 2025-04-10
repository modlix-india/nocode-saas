package com.fincity.saas.entity.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactivefeign.spring.config.EnableReactiveFeignClients;

@ComponentScan(basePackages = "com.fincity")

@EnableFeignClients
@EnableReactiveFeignClients(basePackages = "com.fincity")
@EnableWebFlux
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true)

@SpringBootApplication
public class EntityCollectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(EntityCollectorApplication.class, args);
	}

}
