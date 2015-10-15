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

import lombok.SneakyThrows;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import rx.Observable;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixObservableCommand;

import java.lang.reflect.Method;

/**
 * @author Spencer Gibb
 */
@Aspect
public class CircuitBreakerAspect {

	@Pointcut("@annotation(org.springframework.cloud.netflix.hystrix.CircuitBreaker)")
	public void circuitBreakerAnnotationPointcut() {
	}

	@SneakyThrows
	@Around("circuitBreakerAnnotationPointcut()")
	public Object methodsAnnotatedWithHystrixCommand(final ProceedingJoinPoint joinPoint) throws Throwable {
		if (joinPoint.getSignature() instanceof MethodSignature) {
			MethodSignature signature = (MethodSignature) joinPoint.getSignature();
			String groupKeyName = signature.getName();
			HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey(groupKeyName);
			String setterKeyName = signature.getName();

			Class returnType = signature.getReturnType();

			CircuitBreaker circuitBreaker = signature.getMethod().getAnnotation(CircuitBreaker.class);
			final Method fallback;
			if (StringUtils.hasText(circuitBreaker.value())) {
				fallback = ReflectionUtils.findMethod(joinPoint.getTarget().getClass(),
						circuitBreaker.value(), signature.getParameterTypes());
				ReflectionUtils.makeAccessible(fallback);
			} else {
				fallback = null;
			}

			//TODO: turn into factory
			//TODO: support adapter like javanica
			if (Observable.class.isAssignableFrom(returnType)) {
				//TODO: support hot/cold https://github.com/Netflix/Hystrix/wiki/How-To-Use#Reactive-Execution
				HystrixObservableCommand.Setter setter = HystrixObservableCommand.Setter.withGroupKey(groupKey)
						.andCommandKey(HystrixCommandKey.Factory.asKey(setterKeyName));
				return new HystrixObservableCommand<Object>(setter) {
					@Override
					@SneakyThrows
					@SuppressWarnings("unchecked")
					protected Observable<Object> construct() {
						return Observable.class.cast(joinPoint.proceed());
					}

					@Override
					@SneakyThrows
					@SuppressWarnings("unchecked")
					protected Observable<Object> resumeWithFallback() {
						if (fallback != null) {
							return (Observable<Object>) fallback.invoke(joinPoint.getTarget(), joinPoint.getArgs());
						}
						return super.resumeWithFallback();
					}
				}.toObservable();
			} else {
				HystrixCommand.Setter setter = HystrixCommand.Setter.withGroupKey(groupKey)
						.andCommandKey(HystrixCommandKey.Factory.asKey(setterKeyName));
				return new HystrixCommand<Object>(setter) {
					@Override
					@SneakyThrows
					protected Object run() throws Exception {
						return joinPoint.proceed();
					}

					@Override
					@SneakyThrows
					protected Object getFallback() {
						if (fallback != null) {
							return fallback.invoke(joinPoint.getTarget(), joinPoint.getArgs());
						}
						return super.getFallback();
					}
				}.execute();
			}
		}

		throw new IllegalStateException("Unable to create circuit breaker for " + joinPoint.toLongString());
	}
}
