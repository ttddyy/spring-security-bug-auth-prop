/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.demosecurityauthpropagation;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Tadaya Tsuyukubo
 */
@SpringJUnitConfig
public class MyTest {

	// To run this test, it needs to have "spring-boot-starter-oauth2-resource-server" and "spring-boot-starter-oauth2-client"
	// dependencies which triggers "OAuth2ImportSelector" to import "SecurityReactorContextConfiguration".
	//

	// from SecurityReactorContextConfiguration.SecurityReactorContextSubscriberRegistrar#SECURITY_REACTOR_CONTEXT_OPERATOR_KEY
	// also used by ServletOAuth2AuthorizedClientExchangeFilterFunction
	static final String SECURITY_REACTOR_CONTEXT_ATTRIBUTES_KEY = "org.springframework.security.SECURITY_CONTEXT_ATTRIBUTES";

	@Test
	@WithMockUser("foo")
	void demoTest() {
		Authentication authInMainThread = TestSecurityContextHolder.getContext().getAuthentication();

		AtomicReference<Authentication> authInFilter = new AtomicReference<>();
		WebClient webClient = WebClient.builder()
//				.filter(new ServletOAuth2AuthorizedClientExchangeFilterFunction())
				.filter((request, next) -> {
					return Mono.deferContextual(context -> {
						Map<Object, Object> contextAttributes = context.get(SECURITY_REACTOR_CONTEXT_ATTRIBUTES_KEY);
						Authentication auth = (Authentication) contextAttributes.get(Authentication.class);
						authInFilter.set(auth);
						return next.exchange(request);
					});
				}).build();

		webClient.get().uri("https://vmware.com")
				.retrieve()
				.bodyToMono(String.class)
				.subscribeOn(Schedulers.boundedElastic())  // <-- use different thread to make a call
				.block();

		// This check passes on Boot 2.6 or not using "subscribeOn"
		assertThat(authInFilter.get()).isSameAs(authInMainThread);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebSecurity
	static class MyConfig {

	}
}
