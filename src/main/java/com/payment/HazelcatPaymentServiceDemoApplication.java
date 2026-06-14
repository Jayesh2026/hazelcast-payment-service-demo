package com.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching      // activates @Cacheable, @CachePut, @CacheEvict
@EnableScheduling   // activates @Scheduled jobs (retry consumer, etc.)
public class HazelcatPaymentServiceDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(HazelcatPaymentServiceDemoApplication.class, args);
	}

}
