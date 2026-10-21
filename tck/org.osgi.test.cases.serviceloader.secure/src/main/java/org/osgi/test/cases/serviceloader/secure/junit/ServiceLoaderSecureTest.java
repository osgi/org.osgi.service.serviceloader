/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0 
 *******************************************************************************/

package org.osgi.test.cases.serviceloader.secure.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.test.cases.serviceloader.secure.export.TestBridge;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectBundleInstaller;
import org.osgi.test.common.install.BundleInstaller;

public class ServiceLoaderSecureTest {

	@InjectBundleContext
	BundleContext		context;

	@InjectBundleInstaller
	BundleInstaller		installer;
	private Bundle spiBundle;
	
	@BeforeEach
	public void setUp() throws Exception {

		spiBundle = installer.installBundle("spi.jar", false);
		spiBundle.start();
	}
	
	@AfterEach
	public void tearDown() throws Exception {
		
		spiBundle.stop();
		spiBundle.uninstall();
	}
	
	/**
	 * Install a single provider with permissions to register services. Installs a client looking up the provider as OSGi service.
	 * Expected behavior is the same as in non-secure setup. The client should be wired to the provider and be able to obtain the
	 * provider service.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testServiceRegistrationWithPermission() throws Exception {
		Bundle implBundle = installer.installBundle("implregister.jar", false);
		assertThat(implBundle).isNotNull();
		
		Bundle mediatorBundle = null;
		
		try {
			implBundle.start();
			
			Thread.sleep(500); // wait 500 ms in case the extender takes a little to extend this bundle
			
			// find the Mediator bundle
			BundleRevision rev = implBundle.adapt(BundleRevision.class);
			List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
			assertThat(wires).isNotNull();
			BundleWire wire = wires.get(0);
			assertThat(wire).isNotNull();
			mediatorBundle = wire.getProvider().getBundle();
			assertThat(mediatorBundle).isNotNull();
			
			ServiceReference<?>[] refs = implBundle.getRegisteredServices();
			assertThat(refs).isNotNull();
			assertThat(refs.length).as("only one implementation should have been registered").isOne();
			assertThat(refs[0].getProperty("serviceloader.mediator")).as("expecting serviceloader.mediator property to contain id of mediator bundle").isEqualTo(mediatorBundle.getBundleId());
			assertThat(refs[0].getProperty("provider")).as("public property must be registered with the service").isEqualTo("TCK");
			assertThat(refs[0].getProperty(".hint")).as("private properties must not be registered with the service").isNull();

			Bundle client = installer.installBundle("clientosgi.jar", false);
			assertThat(client).isNotNull();
			
    		try {
    			client.start();

    			rev = client.adapt(BundleRevision.class);
    			wires = rev.getWiring().getRequiredWires("osgi.extender");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting exactly one mediator wired").hasSize(1);

    			wires = rev.getWiring().getRequiredWires("osgi.serviceloader");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting 1 provider to be wired").hasSize(1);

    			Collection<ServiceReference<TestBridge>> reffs = context.getServiceReferences(TestBridge.class, "(test=client)");
    			assertThat(reffs).isNotNull();
    			assertThat(reffs).hasSize(1);

    			TestBridge service = context.getService(reffs.iterator().next());
    			assertThat(service).as("client bundle did not register its service").isNotNull();

    			service.run("green");
    		} finally {
    			client.stop();
    			client.uninstall();
    		}
			
		} finally {
			implBundle.stop();
			implBundle.uninstall();
		}
	}
	
	
	/**
	 * Installs a single provider without service registration permissions. The client is expected not to be able to obtain
	 * the service from the service registry. Otherwise, the mediator would have granted the provider more permissions
	 * than are configured.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testServiceRegistrationWithOutPermission() throws Exception {
		Bundle implBundle = installer.installBundle("implregister_noperm.jar", false);
		assertThat(implBundle).isNotNull();
		
		Bundle mediatorBundle = null;
		
		try {
			implBundle.start();
			
			Thread.sleep(500); // wait 500 ms in case the extender takes a little to extend this bundle
			
			// find the Mediator bundle
			BundleRevision rev = implBundle.adapt(BundleRevision.class);
			List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
			assertThat(wires).isNotNull();
			BundleWire wire = wires.get(0);
			assertThat(wire).isNotNull();
			mediatorBundle = wire.getProvider().getBundle();
			assertThat(mediatorBundle).isNotNull();
			
			Bundle client = installer.installBundle("clientosgi.jar", false);
			assertThat(client).isNotNull();
			
    		try {
    			client.start();

    			rev = client.adapt(BundleRevision.class);
    			wires = rev.getWiring().getRequiredWires("osgi.extender");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting exactly one mediator wired").hasSize(1);

    			wires = rev.getWiring().getRequiredWires("osgi.serviceloader");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting 1 provider to be wired").hasSize(1);

    			Collection<ServiceReference<TestBridge>> reffs = context.getServiceReferences(TestBridge.class, "(test=client)");
    			assertThat(reffs).isNotNull();
    			assertThat(reffs).hasSize(1);

    			TestBridge service = context.getService(reffs.iterator().next());
    			assertThat(service).as("client bundle did not register its service").isNotNull();

    			service.run(null);
    		} finally {
    			client.stop();
    			client.uninstall();
    		}
			
		} finally {
			implBundle.stop();
			implBundle.uninstall();
		}
	}
	
	
	/**
	 * Installs a single provider with service registration permissions and a client bundle without service GET permissions.
	 * The client is expected not to be able to obtain the service from the service registry. Otherwise, the mediator would
	 * have granted the provider more permissions than are configured.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testClientWithOutPermission() throws Exception {
		Bundle implBundle = installer.installBundle("implregister.jar", false);
		assertThat(implBundle).isNotNull();
		
		Bundle mediatorBundle = null;
		
		try {
			implBundle.start();
			
			Thread.sleep(500); // wait 500 ms in case the extender takes a little to extend this bundle
			
			// find the Mediator bundle
			BundleRevision rev = implBundle.adapt(BundleRevision.class);
			List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
			assertThat(wires).isNotNull();
			BundleWire wire = wires.get(0);
			assertThat(wire).isNotNull();
			mediatorBundle = wire.getProvider().getBundle();
			assertThat(mediatorBundle).isNotNull();
			
			ServiceReference<?>[] refs = implBundle.getRegisteredServices();
			assertThat(refs).isNotNull();
			assertThat(refs.length).as("only one implementation should have been registered").isOne();
			assertThat(refs[0].getProperty("serviceloader.mediator")).as("expecting serviceloader.mediator property to contain id of mediator bundle").isEqualTo(mediatorBundle.getBundleId());
			assertThat(refs[0].getProperty("provider")).as("public property must be registered with the service").isEqualTo("TCK");
			assertThat(refs[0].getProperty(".hint")).as("private properties must not be registered with the service").isNull();

			Bundle client = installer.installBundle("clientosgi_noperm.jar", false);
			assertThat(client).isNotNull();
			
    		try {
    			client.start();

    			rev = client.adapt(BundleRevision.class);
    			wires = rev.getWiring().getRequiredWires("osgi.extender");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting exactly one mediator wired").hasSize(1);

    			wires = rev.getWiring().getRequiredWires("osgi.serviceloader");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting 1 provider to be wired").hasSize(1);

    			Collection<ServiceReference<TestBridge>> reffs = context.getServiceReferences(TestBridge.class, "(test=client)");
    			assertThat(reffs).isNotNull();
    			assertThat(reffs).hasSize(1);

    			TestBridge service = context.getService(reffs.iterator().next());
    			assertThat(service).as("client bundle did not register its service").isNotNull();

    			service.run(null);
    		} finally {
    			client.stop();
    			client.uninstall();
    		}
			
		} finally {
			implBundle.stop();
			implBundle.uninstall();
		}
	}
	
	
	/**
	 * Installs a single provider with service registration permissions and a client bundle with service GET permissions.
	 * The client is expected to be able to obtain the provided service from the ServiceLoader.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testLegacyClientWithPermission() throws Exception {
		Bundle implBundle = installer.installBundle("implregister.jar", false);
		assertThat(implBundle).isNotNull();
		
		Bundle mediatorBundle = null;
		
		try {
			implBundle.start();
			
			Thread.sleep(500); // wait 500 ms in case the extender takes a little to extend this bundle
			
			// find the Mediator bundle
			BundleRevision rev = implBundle.adapt(BundleRevision.class);
			List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
			assertThat(wires).isNotNull();
			BundleWire wire = wires.get(0);
			assertThat(wire).isNotNull();
			mediatorBundle = wire.getProvider().getBundle();
			assertThat(mediatorBundle).isNotNull();
			
			ServiceReference<?>[] refs = implBundle.getRegisteredServices();
			assertThat(refs).isNotNull();
			assertThat(refs.length).as("only one implementation should have been registered").isOne();
			assertThat(refs[0].getProperty("serviceloader.mediator")).as("expecting serviceloader.mediator property to contain id of mediator bundle").isEqualTo(mediatorBundle.getBundleId());
			assertThat(refs[0].getProperty("provider")).as("public property must be registered with the service").isEqualTo("TCK");
			assertThat(refs[0].getProperty(".hint")).as("private properties must not be registered with the service").isNull();

			Bundle client = installer.installBundle("client.jar", false);
			assertThat(client).isNotNull();
			
    		try {
    			client.start();

    			rev = client.adapt(BundleRevision.class);
    			wires = rev.getWiring().getRequiredWires("osgi.extender");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting exactly one mediator wired").hasSize(1);

    			wires = rev.getWiring().getRequiredWires("osgi.serviceloader");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting 1 provider to be wired").hasSize(1);

    			Collection<ServiceReference<TestBridge>> reffs = context.getServiceReferences(TestBridge.class, "(test=client)");
    			assertThat(reffs).isNotNull();
    			assertThat(reffs).hasSize(1);

    			TestBridge service = context.getService(reffs.iterator().next());
    			assertThat(service).as("client bundle did not register its service").isNotNull();

    			service.run("green");
    		} finally {
    			client.stop();
    			client.uninstall();
    		}
			
		} finally {
			implBundle.stop();
			implBundle.uninstall();
		}
	}
	
	
	/**
	 * Installs a single provider with service registration permissions and a client bundle without service GET permissions.
	 * The legacy client is expected not to be able to use the service from the ServiceLoader. Otherwise, the mediator would
	 * have granted the provider more permissions than are configured.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testLegacyClientWithOutPermission() throws Exception {
		Bundle implBundle = installer.installBundle("implregister.jar", false);
		assertThat(implBundle).isNotNull();
		
		Bundle mediatorBundle = null;
		
		try {
			implBundle.start();
			
			Thread.sleep(500); // wait 500 ms in case the extender takes a little to extend this bundle
			
			// find the Mediator bundle
			BundleRevision rev = implBundle.adapt(BundleRevision.class);
			List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
			assertThat(wires).isNotNull();
			BundleWire wire = wires.get(0);
			assertThat(wire).isNotNull();
			mediatorBundle = wire.getProvider().getBundle();
			assertThat(mediatorBundle).isNotNull();
			
			ServiceReference<?>[] refs = implBundle.getRegisteredServices();
			assertThat(refs).isNotNull();
			assertThat(refs.length).as("only one implementation should have been registered").isOne();
			assertThat(refs[0].getProperty("serviceloader.mediator")).as("expecting serviceloader.mediator property to contain id of mediator bundle").isEqualTo(mediatorBundle.getBundleId());
			assertThat(refs[0].getProperty("provider")).as("public property must be registered with the service").isEqualTo("TCK");
			assertThat(refs[0].getProperty(".hint")).as("private properties must not be registered with the service").isNull();

			Bundle client = installer.installBundle("client_noperm.jar", false);
			assertThat(client).isNotNull();
			
    		try {
    			client.start();

    			rev = client.adapt(BundleRevision.class);
    			wires = rev.getWiring().getRequiredWires("osgi.extender");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting exactly one mediator wired").hasSize(1);

    			wires = rev.getWiring().getRequiredWires("osgi.serviceloader");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting 1 provider to be wired").hasSize(1);

    			Collection<ServiceReference<TestBridge>> reffs = context.getServiceReferences(TestBridge.class, "(test=client)");
    			assertThat(reffs).isNotNull();
    			assertThat(reffs).hasSize(1);

    			TestBridge service = context.getService(reffs.iterator().next());
    			assertThat(service).as("client bundle did not register its service").isNotNull();

    			service.run(null);
    		} finally {
    			client.stop();
    			client.uninstall();
    		}
			
		} finally {
			implBundle.stop();
			implBundle.uninstall();
		}
	}
	
    
}
