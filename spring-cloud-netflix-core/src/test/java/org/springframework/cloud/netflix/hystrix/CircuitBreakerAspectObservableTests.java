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
import static org.junit.Assert.fail;

import java.util.Collection;

import lombok.AllArgsConstructor;
import lombok.Data;

import lombok.SneakyThrows;
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
import rx.Subscriber;
import rx.functions.Action1;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

/**
 * @author Spencer Gibb
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {CircuitBreakerAspectObservableTests.ObservableTestConfig.class})
public class CircuitBreakerAspectObservableTests {

	@Autowired
	private UserService userService;

	@Test
	public void testGetUserByIdObservable() {
		HystrixRequestContext context = HystrixRequestContext.initializeContext();
		try {

			// blocking
			assertEquals("name: 1", userService.getUser("1", "name: ").toBlocking().single().getName());

			// non-blocking
			// - this is a verbose anonymous inner-class approach
			Observable<User> fUser = userService.getUser("1", "name: ");
			fUser.subscribe(new Observer<User>() {

				@Override
				public void onCompleted() {
					// nothing needed here
				}

				@Override
				@SneakyThrows
				public void onError(Throwable e) {
					e.printStackTrace();
					throw e;
				}

				@Override
				public void onNext(User u) {
					assertEquals("name: 1", u.getName());
				}

			});

			Observable<User> fs = userService.getUser("1", "name: ");
			fs.subscribe(new Action1<User>() {

				@Override
				public void call(User user) {
					assertEquals("name: 1", user.getName());
				}
			});
			assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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

			// blocking
			assertEquals(exUser, userService.getUser(" ", "").toBlocking().single());
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
		public Observable<User> getUser(final String id, final String name) {
			//return Observable.just(new User(id, name + id));
			return Observable.create(new Observable.OnSubscribe<User>() {
				@Override
				public void call(Subscriber<? super User> observer) {
					try {
						if (!observer.isUnsubscribed()) {
							validate(id, name);
							// a real example would do work like a network call here
							observer.onNext(new User(id, name + id));
							observer.onCompleted();
						}
					} catch (Exception e) {
						observer.onError(e);
					}
				}
			} );
		}

		private Observable<User> staticFallback(String id, String name) {
			return Observable.just(new User("def", "def"));
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
