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
import static org.assertj.core.api.Assertions.fail;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.test.cases.serviceloader.export.TestBridge;
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
public class ServiceLoaderServiceClientTest {

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
	 * Test Case 2.1.1:
	 *  Install a client bundle that doesn't have the requirement on the mediator. Ensure it is not extended (wired).
	 * 
	 * @throws Exception
	 */
	@Test
	public void testNotExtended() throws Exception {
        Bundle implBundle = installer.installBundle("implexplicitregister.jar", false);
        implBundle.start();

        try {
        	Bundle clientBundle = installer.installBundle("clientnotextended.jar", false);
        	assertThat(clientBundle).isNotNull();

        	try {
        		clientBundle.start();
        		
        		BundleRevision rev = clientBundle.adapt(BundleRevision.class);
        		List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
        		assertThat(wires).isNotNull();
        		assertThat(wires).isEmpty();


        		Collection<ServiceReference<TestBridge>> refs = context.getServiceReferences(TestBridge.class, "(test=client)");
        		assertThat(refs).isNotNull();
        		assertThat(refs).hasSize(1);

        		TestBridge service = context.getService(refs.iterator().next());
        		assertThat(service).as("client bundle did not register its service").isNotNull();

        		try {
        			service.run("green");

        			fail("Expecting the client not to find a provider");
        		} catch (AssertionError ex) {
        		}
        	} finally {
        		clientBundle.stop();
        		clientBundle.uninstall();
        	}
        } finally {
        	implBundle.stop();
        	implBundle.uninstall();
        }
	}

    /**
     * TestCase 2.2:
     *  
     * @throws Exception
     */
    @Test
    public void testServiceClient() throws Exception {
        Bundle implBundle = installer.installBundle("implfirstprovider.jar", false);
        implBundle.start();

        try {
        	final Bundle client = installer.installBundle("client.jar", false);
        	assertThat(client).isNotNull();
        	
        	try {
        		client.start();

        		BundleRevision rev = client.adapt(BundleRevision.class);
        		List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
        		assertThat(wires).isNotNull();
        		assertThat(wires.isEmpty()).isFalse();
        		
        		Collection<ServiceReference<TestBridge>> refs = context.getServiceReferences(TestBridge.class, "(test=client)");
        		assertThat(refs).isNotNull();
        		assertThat(refs.size()).isEqualTo(1);

        		TestBridge service = context.getService(refs.iterator().next());
        		assertThat(service).as("client bundle did not register its service").isNotNull();

        		service.run("green");
        		
        		// 2.5.1: OPTIONALLY, the client bundle is refreshed when the provider bundle is stopped
        		// thus, there is no assertion here
        		final CountDownLatch latch = new CountDownLatch(1);
        		FrameworkListener listener = new FrameworkListener() {
					
					@Override
					public void frameworkEvent(FrameworkEvent frameworkEvent) {
						if (frameworkEvent.getBundle().equals(client) && frameworkEvent.getType() == FrameworkEvent.PACKAGES_REFRESHED) {
							latch.countDown();
						}
					}
				};
        		context.addFrameworkListener(listener);
        		
        		try {
        			implBundle.stop();
        			
        			if (latch.await(5, TimeUnit.SECONDS)) {
        				System.out.println("133.3.4: client bundle was refreshed after provider bundle was stopped");
        			} else {
        				System.out.println("133.3.4: client bundle was not refreshed after provider bundle was stopped");
        			}
        		} finally {
        			context.removeFrameworkListener(listener);
        		}
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
     * TestCase 2.2.2:
     * Multiple providers are registered and cardinality:=multiple is set on requirement.
     * 
     * Not all frameworks may support wiring to multiple or all possible providers.
     * Therefore, the test case introspects the wiring between the client and the provider
     * bundles and only tests for as many service providers as there are wires to the provider
     * bundles. In order to increase the likelihood of getting wired to multiple providers,
     * the providers are not setting the uses constraint on the capability.
     *  
     * @throws Exception
     */
    @Test
    public void testServiceClientWithFilter() throws Exception {
    	Bundle implBundle = installer.installBundle("implfirstprovider.jar", false);
    	implBundle.start();
    	try {
    		Bundle implBundle2 = installer.installBundle("implsecondprovider.jar", false);
    		implBundle2.start();

    		try {
    			Bundle client = installer.installBundle("clientfilter.jar", false);
    			assertThat(client).isNotNull();

    			try {
    				client.start();

    				BundleRevision rev = client.adapt(BundleRevision.class);
    				List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
    				assertThat(wires).isNotNull();
    				assertThat(wires).as("expecting exactly one mediator wired").hasSize(1);

    				wires = rev.getWiring().getRequiredWires("osgi.serviceloader");
    				assertThat(wires).isNotNull();

					assertThat(wires).as(
							"There should be 2 wires as there are 2 providers, but 1 wire is also allowed since cardinality:=multiple is not guaranteed to provide all wires")
							.hasSizeBetween(1, 2);

    				Collection<ServiceReference<TestBridge>> refs = context.getServiceReferences(TestBridge.class, "(test=client)");
    				assertThat(refs).isNotNull();
    				assertThat(refs).hasSize(1);

    				TestBridge service = context.getService(refs.iterator().next());
    				assertThat(service).as("client bundle did not register its service").isNotNull();

    				service.run("2");
    			} finally {
    				client.stop();
    				client.uninstall();
    			}
    		} finally {
    			implBundle2.stop();
    			implBundle2.uninstall();
    		}
    	} finally {
    		implBundle.stop();
    		implBundle.uninstall();
    	}
    }

    /**
     * TestCase 2.2.3:
     * Client bundle restricts potential providers based on filter:="(type=two)"
     *  
     * @throws Exception
     */
    @Test
    public void testServiceClientWithFilterOne() throws Exception {
    	Bundle implBundle = installer.installBundle("implfirstprovider.jar", false);
    	implBundle.start();
    	try {
    		Bundle implBundle2 = installer.installBundle("implsecondprovider.jar", false);
    		implBundle2.start();

    		try {
    			Bundle client = installer.installBundle("clientfilterone.jar", false);
    			assertThat(client).isNotNull();

    			try {
    				client.start();

    				BundleRevision rev = client.adapt(BundleRevision.class);
    				List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
    				assertThat(wires).isNotNull();
    				assertThat(wires).as("expecting exactly one mediator wired").hasSize(1);
    				
    				wires = rev.getWiring().getRequiredWires("osgi.serviceloader");
    				assertThat(wires).isNotNull();
    				assertThat(wires).as("expecting 1 provider to be wired").hasSize(1);

    				Collection<ServiceReference<TestBridge>> refs = context.getServiceReferences(TestBridge.class, "(test=client)");
    				assertThat(refs).isNotNull();
    				assertThat(refs).hasSize(1);

    				TestBridge service = context.getService(refs.iterator().next());
    				assertThat(service).as("client bundle did not register its service").isNotNull();

    				service.run("red");
    			} finally {
    				client.stop();
    				client.uninstall();
    			}
    		} finally {
    			implBundle2.stop();
    			implBundle2.uninstall();
    		}
    	} finally {
    		implBundle.stop();
    		implBundle.uninstall();
    	}
    }

    /**
     * TestCase 2.3.1:
     * Client bundle restricts potential services based on filter:="(type=two)"
     *  
     * @throws Exception
     */
    @Test
    public void testServiceClientOSGi() throws Exception {
    	Bundle implBundle = installer.installBundle("implfirstprovider.jar", false);
    	implBundle.start();
    	try {
    		Bundle implBundle2 = installer.installBundle("implsecondprovider.jar", false);
    		implBundle2.start();

    		try {
    			Bundle client = installer.installBundle("clientosgi.jar", false);
    			assertThat(client).isNotNull();

    			try {
    				client.start();

    				BundleRevision rev = client.adapt(BundleRevision.class);
    				List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
    				assertThat(wires).isNotNull();
    				assertThat(wires).as("expecting exactly one mediator wired").hasSize(1);
    				
    				Collection<ServiceReference<TestBridge>> refs = context.getServiceReferences(TestBridge.class, "(test=client)");
    				assertThat(refs).isNotNull();
    				assertThat(refs).hasSize(1);

    				TestBridge service = context.getService(refs.iterator().next());
    				assertThat(service).as("client bundle did not register its service").isNotNull();

    				service.run("red");
    			} finally {
    				client.stop();
    				client.uninstall();
    			}
    		} finally {
    			implBundle2.stop();
    			implBundle2.uninstall();
    		}
    	} finally {
    		implBundle.stop();
    		implBundle.uninstall();
    	}
    }

    /**
     * TestCase 2.5.2:
     * Client is wired to provider bundle. Stop the provider bundle. The service provided by the stopped bundle
     * does not show up in the iteration.
     *  
     * @throws Exception
     */
    @Test
    public void testServiceClientLifecycle() throws Exception {
    	Bundle implBundle = installer.installBundle("implfirstprovider.jar", false);
    	implBundle.start();
    	try {
    		Bundle client = installer.installBundle("clientlifecycle.jar", false);
    		assertThat(client).isNotNull();

    		try {
    			client.start();

    			BundleRevision rev = client.adapt(BundleRevision.class);
    			List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting exactly one mediator wired").hasSize(1);

    			wires = rev.getWiring().getRequiredWires("osgi.serviceloader");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting 1 provider to be wired").hasSize(1);

    			Collection<ServiceReference<TestBridge>> refs = context.getServiceReferences(TestBridge.class, "(test=client)");
    			assertThat(refs).isNotNull();
    			assertThat(refs).hasSize(1);

    			TestBridge service = context.getService(refs.iterator().next());
    			assertThat(service).as("client bundle did not register its service").isNotNull();

    			// stop the first provider bundle
    			implBundle.stop();

    			// iterator must return no provider, since its only provider was stopped
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
     * Test Case 2.4.1:
     *  Once a client is wired to a bundle because of its dependency on the serviceloader namespace, and it matches a requirement for a single service type, it
     *  gains access to all services of that same service type. That is, the implementation cannot ensure the visibility to a single implementation of a given service type.
     * @throws Exception
     */
    @Test
    public void testAccessToAllServices() throws Exception {
    	Bundle implBundle = installer.installBundle("implmultiprovider.jar", false);
    	implBundle.start();
    	
    	try {
    		Bundle client = installer.installBundle("legacyclient.jar", false);
    		assertThat(client).isNotNull();

    		try {
    			client.start();

    			BundleRevision rev = client.adapt(BundleRevision.class);
    			List<BundleWire> wires = rev.getWiring().getRequiredWires("osgi.extender");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting exactly one mediator wired").hasSize(1);

    			wires = rev.getWiring().getRequiredWires("osgi.serviceloader");
    			assertThat(wires).isNotNull();
    			assertThat(wires).as("expecting 1 provider to be wired").hasSize(1);

    			Collection<ServiceReference<TestBridge>> refs = context.getServiceReferences(TestBridge.class, "(test=client)");
    			assertThat(refs).isNotNull();
    			assertThat(refs).hasSize(1);

    			TestBridge service = context.getService(refs.iterator().next());
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
