/*
 * RHQ Management Platform
 * Copyright (C) 2012 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.modules.plugins.jbossas7.itest.domain;

import org.testng.annotations.Test;

import org.rhq.core.domain.resource.ResourceCategory;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.modules.plugins.jbossas7.itest.AbstractServerComponentTest;
import org.rhq.test.arquillian.RunDiscovery;

/**
 * Test discovery and facets of the "JBossAS7 Host Controller" Resource type.
 *
 * @author Ian Springer
 */
@Test(groups = "pc, domain", singleThreaded = true)
public class DomainServerComponentTest extends AbstractServerComponentTest {

    public static final ResourceType RESOURCE_TYPE = new ResourceType("JBossAS7 Host Controller", PLUGIN_NAME, ResourceCategory.SERVER, null);
    public static final String RESOURCE_KEY = "DomainController";

    @Override
    protected ResourceType getServerResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    protected String getServerResourceKey() {
        return RESOURCE_KEY;
    }

    @Override
    @Test(groups = "pc")
    @RunDiscovery
    public void testAutoDiscovery() throws Exception {
        super.testAutoDiscovery();
    }

    // ******************************* METRICS ******************************* //
    @Override
    @Test(dependsOnMethods = "testAutoDiscovery")
    public void testMetricsHaveNonNullValues() throws Exception {
        super.testMetricsHaveNonNullValues();
    }

    @Override
    @Test(dependsOnMethods = "testAutoDiscovery")
    public void testReleaseVersionTrait() throws Exception {
        super.testReleaseVersionTrait();
    }

    // ******************************* OPERATIONS ******************************* //
    // TODO: Re-enable this once "shutdown" operation has been fixed.
    @Test(dependsOnMethods = "testAutoDiscovery", enabled = false)
    public void testShutdownAndStartOperations() throws Exception {
        super.testShutdownAndStartOperations();
    }

}