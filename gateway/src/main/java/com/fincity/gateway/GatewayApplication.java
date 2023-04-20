package com.fincity.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
EnableDiscoveryClient
@EnableFeignClients
@EnableCaching
@EnableReactiveFeignClients(basePackages =  "com.fincity")
@ComponentScan(basePackages = "com.fincity")
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

}
