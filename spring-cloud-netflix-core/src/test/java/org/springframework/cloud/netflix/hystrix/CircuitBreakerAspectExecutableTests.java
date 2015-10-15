/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.netflix.hystrix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import lombok.AllArgsConstructor;
import lombok.Data;

import org.apache.commons.lang3.Validate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import rx.Observable;
import rx.Observer;
import rx.functions.Action1;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

/**
 * @author Spencer Gibb
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {CircuitBreakerAspectExecutableTests.ObservableTestConfig.class})
public class CircuitBreakerAspectExecutableTests {

	@Autowired
	private UserService userService;

	@Test
	public void testGetUserByIdObservable() {
		HystrixRequestContext context = HystrixRequestContext.initializeContext();
		try {

			// blocking
			assertEquals("name: 1", userService.getUser("1", "name: ").getName());

			User user = userService.getUser("1", "name: ");
			assertEquals("name: 1", user.getName());
			assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
			HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getUser");
			assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.SUCCESS));
		} finally {
			context.shutdown();
		}
	}

	@Test
	public void testGetUserWithFallback() {
		HystrixRequestContext context = HystrixRequestContext.initializeContext();
		try {
			final User exUser = new User("def", "def");

			assertEquals(exUser, userService.getUser(" ", ""));
			assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
			HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getUser");
			// confirm that command has failed
			assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
			// and that fallback was successful
			assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
		} finally {
			context.shutdown();
		}
	}

	public static HystrixInvokableInfo getHystrixCommandByKey(String key) {
		HystrixInvokableInfo hystrixCommand = null;
		Collection<HystrixInvokableInfo<?>> executedCommands =
				HystrixRequestLog.getCurrentRequest().getAllExecutedCommands();
		for (HystrixInvokableInfo command : executedCommands) {
			if (command.getCommandKey().name().equals(key)) {
				hystrixCommand = command;
				break;
			}
		}
		return hystrixCommand;
	}

	public static class UserService {

		@CircuitBreaker("staticFallback")
		public User getUser(String id, String name) {
			validate(id, name);
			return new User(id, name + id);
		}

		private User staticFallback(String id, String name) {
			return new User("def", "def");
		}

		private void validate(String id, String name) {
			Validate.notBlank(id);
			Validate.notBlank(name);
		}
	}

	@Configuration
	@EnableAspectJAutoProxy(proxyTargetClass = true)
	public static class ObservableTestConfig {

		@Bean
		public CircuitBreakerAspect circuitBreakerAspect() {
			return new CircuitBreakerAspect();
		}

		@Bean
		public UserService userService() {
			return new UserService();
		}
	}

	@Data
	@AllArgsConstructor
	public static class User {
		private String id;
		private String name;
	}

}
