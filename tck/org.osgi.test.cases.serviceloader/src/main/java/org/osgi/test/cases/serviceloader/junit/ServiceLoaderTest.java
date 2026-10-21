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

package org.osgi.test.cases.serviceloader.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectBundleInstaller;
import org.osgi.test.common.install.BundleInstaller;

/**
 * TODO Test cases to write:
 * <ul>
 * <li> 134.3.2 provide API in two different versions and have client and server wire to different versions. Ensure that the mediator does not wire them
 * <li> 134.3.3 stop the provider bundle and ensure that the client does not find the provider
 * <li> 134.3.4 client must not see a provider it is not wired to
 * <li> 134.4.2 create service provider w/ advertisement but w/o publishing, ensure client does not find it
 * <li> 134.4.2 different publishing scenarios with custom properties for the OSGi service, wild cards in capability, etc., uses constraints
 * <li> 134.4.2 write a client that uses the service provider from the OSGi registry directly
 * <li> 134.5.1 verify registered provider service
 * </ul>
 * @author <a href="mailto:tdiekman@tibco.com">Tim Diekmann</a>
 *
 * @since 1.0.0
 */
public class ServiceLoaderTest {

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
	 * 3.1.1 Install a provider w/o the requirement on the mediator -> no provider gets registered
	 * 
	 * @throws Exception
	 */
	@Test
	public void testImplNoExtender() throws Exception {
		Bundle implBundle = installer.installBundle("implnoreq.jar", false);
		assertThat(implBundle).isNotNull();
		
		try {
			implBundle.start();
			
			Thread.sleep(500); // wait 500 ms in case the extender takes a little to extend this bundle
		
			assertThat(implBundle.getRegisteredServices()).as("bundle should not be extended and have registered services").isNull();
		} finally {
			implBundle.uninstall();
		}
	}
	
	/**
	 * 3.1.2 Install a provider and see the registered service, but not from the Mediator
	 * 
	 * @throws Exception
	 */
	@Test
	public void testImplNotExtended() throws Exception {
		Bundle implBundle = installer.installBundle("implnotextended.jar", false);
		assertThat(implBundle).isNotNull();
		
		try {
			implBundle.start();
			
			Thread.sleep(500); // wait 500 ms in case the extender takes a little to extend this bundle
			
			ServiceReference<?>[] refs = implBundle.getRegisteredServices();
			assertThat(refs).isNotNull();
			assertThat(refs[0].getProperty("serviceloader.mediator")).isEqualTo((long)-1);
			
			BundleRevision rev = implBundle.adapt(BundleRevision.class);
			List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
			assertThat(wires).as("bundle must not be extended by the mediator as it doesn't have the requirement set").isEmpty();
		} finally {
			implBundle.uninstall();
		}
	}

	/**
	 * 3.2.3 Install a provider with the requirement on the mediator, but no provided capability -> no provider gets registered
	 * 
	 * @throws Exception
	 */
	@Test
	public void testImplNoCapability() throws Exception {
		Bundle implBundle = installer.installBundle("implnocap.jar", false);
		assertThat(implBundle).isNotNull();
		
		try {
			implBundle.start();
			
			Thread.sleep(500); // wait 500 ms in case the extender takes a little to extend this bundle
		
			BundleRevision rev = implBundle.adapt(BundleRevision.class);
			List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
			assertThat(wires).as("bundle must be wired but not extended by the mediator as it doesn't have the capability set").isNotEmpty();
			
			assertThat(implBundle.getRegisteredServices()).as("bundle should not be extended and have registered services").isNull();
		} finally {
			implBundle.uninstall();
		}
	}

	/**
	 * 3.2.2 Install a provider with the requirement on the mediator, but provided capability w/ wildcards-> no provider gets registered
	 * 
	 * @throws Exception
	 */
	@Test
	public void testImplWildcardCapability() throws Exception {
		Bundle implBundle = installer.installBundle("implwccap.jar", false);
		assertThat(implBundle).isNotNull();
		
		try {
			implBundle.start();
			
			Thread.sleep(500); // wait 500 ms in case the extender takes a little to extend this bundle
		
			BundleRevision rev = implBundle.adapt(BundleRevision.class);
			List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
			assertThat(wires).as("bundle must be wired but not extended by the mediator as its capability uses wildacrd").isNotEmpty();
			
			assertThat(implBundle.getRegisteredServices()).as("bundle should not be extended and have registered services since its capability uses wildcards").isNull();
		} finally {
			implBundle.uninstall();
		}
	}
	
	/**
	 * 3.3 Register a provider.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testAutoRegister() throws Exception {
		Bundle implBundle = installer.installBundle("implautoregister.jar", false);
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
			assertThat(refs).hasSize(2);
			assertThat(refs[0].getProperty("serviceloader.mediator")).as("expecting serviceloader.mediator property to contain id of mediator bundle").isEqualTo(mediatorBundle.getBundleId());
			assertThat(refs[0].getProperty("provider")).as("public property must be registered with the service").isEqualTo("TCK");
			assertThat(refs[1].getProperty("provider")).as("public property must be registered with the service").isEqualTo("TCK");
			assertThat(refs[0].getProperty(".hint")).as("private properties must not be registered with the service").isNull();
			assertThat(refs[1].getProperty(".hint")).as("private properties must not be registered with the service").isNull();
			
			implBundle.stop();
			refs = implBundle.getRegisteredServices();
			assertThat(refs).as("provider services should have been unregistered").isNull();
			
			// starting again
			implBundle.start();
			
			Thread.sleep(500); // wait 500 ms in case the extender takes a little to extend this bundle
			
			refs = implBundle.getRegisteredServices();
			assertThat(refs).as("no services registered").isNotNull();
			assertThat(refs).hasSize(2);
			assertThat(refs[0].getProperty("serviceloader.mediator")).as("expecting serviceloader.mediator property to contain id of mediator bundle").isEqualTo(mediatorBundle.getBundleId());
			assertThat(refs[0].getProperty("provider")).as("public property must be registered with the service").isEqualTo("TCK");
			assertThat(refs[1].getProperty("provider")).as("public property must be registered with the service").isEqualTo("TCK");
			assertThat(refs[0].getProperty(".hint")).as("private properties must not be registered with the service").isNull();
			assertThat(refs[1].getProperty(".hint")).as("private properties must not be registered with the service").isNull();

			mediatorBundle.stop();
			refs = mediatorBundle.getRegisteredServices();
			assertThat(refs).as("provider services should have been unregistered when the mediator is stopped").isNull();
		} finally {
			implBundle.stop();
			implBundle.uninstall();
			
			if (mediatorBundle != null) { 
				mediatorBundle.start();
			}
		}
	}
	
	/**
	 * 3.3 Register a provider with a single service
	 * 
	 * @throws Exception
	 */
	@Test
	public void testExplicitRegistration() throws Exception {
		Bundle implBundle = installer.installBundle("implexplicitregister.jar", false);
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

			implBundle.stop();
			refs = implBundle.getRegisteredServices();
			assertThat(refs).as("provider services should have been unregistered").isNull();
			
		} finally {
			implBundle.stop();
			implBundle.uninstall();
		}
	}
	
	/**
	 * The registered service provider has to be a ServiceFactory (133.5.2 in the spec).
	 * 
	 * @throws Exception
	 */
	@Test
	public void testServiceFactory() throws Exception {
		Bundle implBundle = installer.installBundle("implexplicitregister.jar", false);
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
			assertThat(refs).hasSize(1);
			assertThat(refs[0].getProperty("serviceloader.mediator")).as("expecting serviceloader.mediator property to contain id of mediator bundle").isEqualTo(mediatorBundle.getBundleId());

			// register 2 client bundles and compare the returned service object to make sure they are different
			Bundle client1 = installer.installBundle("client1.jar", false);
			Bundle client2 = installer.installBundle("client2.jar", false);
			
			try {
				client1.start();
				client2.start();

				ServiceReference< ? > ref1 = client1.getBundleContext()
						.getServiceReference(
								"org.osgi.test.cases.serviceloader.spi.ColorProvider");
				assertThat(ref1).isNotNull();
				Object service1 = client1.getBundleContext().getService(ref1);

				ServiceReference< ? > ref2 = client2.getBundleContext()
						.getServiceReference(
								"org.osgi.test.cases.serviceloader.spi.ColorProvider");
				assertThat(ref2).isNotNull();
				Object service2 = client2.getBundleContext().getService(ref2);

				assertThat(service2).as("provider must be registered as a ServiceFactory and return different objects to different clients").isNotSameAs(service1);
			} finally {
				client2.uninstall();
				client1.uninstall();
			}
		} finally {
			implBundle.stop();
			implBundle.uninstall();
		}
	}
	
//    public void testClient() throws Exception {
//        Bundle implBundle = install("impl.jar");
//        Bundle clientBundle = install("client.jar");
//        
//        implBundle.start();
//        clientBundle.start();
//
//    	Collection<ServiceReference<TestBridge>> refs = getContext().getServiceReferences(TestBridge.class, "(test=client)");
//    	assertNotNull(refs);
//    	assertEquals(1, refs.size());
//    	
//        TestBridge service = getContext().getService(refs.iterator().next());
//        assertNotNull("client bundle did not register its service", service);
//
//        service.run();
//    }
    
}
