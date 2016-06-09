/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.netflix.ribbon;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.netflix.archaius.ArchaiusAutoConfiguration;
import org.springframework.cloud.netflix.ribbon.test.TestLoadBalancer;
import org.springframework.cloud.netflix.ribbon.test.TestServerList;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.netflix.loadbalancer.ConfigurationBasedServerList;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.NoOpPing;
import com.netflix.loadbalancer.RandomRule;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerListSubsetFilter;
import com.netflix.loadbalancer.ZoneAvoidanceRule;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;

/**
 * @author Spencer Gibb
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = RibbonClientPreprocessorPropertiesOverridesIntegrationTests.TestConfiguration.class)
@TestPropertySource(properties = {"foo.ribbon.NFLoadBalancerPingClassName=com.netflix.loadbalancer.DummyPing",
		"foo.ribbon.NFLoadBalancerRuleClassName=com.netflix.loadbalancer.RandomRule",
		"foo.ribbon.NIWSServerListClassName=org.springframework.cloud.netflix.ribbon.test.TestServerList",
		"foo.ribbon.NIWSServerListFilterClassName=com.netflix.loadbalancer.ServerListSubsetFilter",
		"foo.ribbon.NFLoadBalancerClassName=org.springframework.cloud.netflix.ribbon.test.TestLoadBalancer",
})
@DirtiesContext
public class RibbonClientPreprocessorPropertiesOverridesIntegrationTests {

	@Autowired
	private SpringClientFactory factory;

	@Test
	public void ruleOverridesToRandom() throws Exception {
		RandomRule.class.cast(getLoadBalancer("foo").getRule());
		ZoneAvoidanceRule.class.cast(getLoadBalancer("bar").getRule());
	}

	@Test
	public void pingOverridesToDummy() throws Exception {
		DummyPing.class.cast(getLoadBalancer("foo").getPing());
		NoOpPing.class.cast(getLoadBalancer("bar").getPing());
	}

	@Test
	public void serverListOverridesToTest() throws Exception {
		TestServerList.class.cast(getLoadBalancer("foo").getServerListImpl());
		ConfigurationBasedServerList.class.cast(getLoadBalancer("bar").getServerListImpl());
	}

	@Test
	public void loadBalancerOverridesToTest() throws Exception {
		TestLoadBalancer.class.cast(getLoadBalancer("foo"));
		ZoneAwareLoadBalancer.class.cast(getLoadBalancer("bar"));
	}

	@Test
	public void serverListFilterOverride() throws Exception {
		ServerListSubsetFilter.class.cast(getLoadBalancer("foo").getFilter());
		ZonePreferenceServerListFilter.class.cast(getLoadBalancer("bar").getFilter());
	}

	@SuppressWarnings("unchecked")
	private ZoneAwareLoadBalancer<Server> getLoadBalancer(String name) {
		return (ZoneAwareLoadBalancer<Server>) this.factory.getLoadBalancer(name);
	}

	@Configuration
	@RibbonClients
	@Import({ UtilAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class,
			ArchaiusAutoConfiguration.class, RibbonAutoConfiguration.class})
	protected static class TestConfiguration {
	}

}
