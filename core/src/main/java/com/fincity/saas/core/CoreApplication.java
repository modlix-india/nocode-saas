package com.fincity.saas.core;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactivefeign.spring.config.EnableReactiveFeignClients;

@ComponentScan(basePackages = "com.fincity")

@EnableFeignClients
@EnableReactiveFeignClients(basePackages = "com.fincity")
@EnableReactiveMongoRepositories(basePackages = "com.fincity")
@EnableWebFlux
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@EnableCaching
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableRabbit

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class CoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(CoreApplication.class, args);
	}

}
