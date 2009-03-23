/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
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
package org.rhq.plugins.jbossas5;

import java.util.Map;

import org.apache.commons.logging.Log;

import org.jboss.managed.api.ManagedProperty;

import org.rhq.core.pluginapi.inventory.ResourceContext;
import org.rhq.core.pluginapi.configuration.ConfigurationUpdateReport;
import org.rhq.core.pluginapi.configuration.ConfigurationFacet;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.configuration.ConfigurationUpdateStatus;
import org.rhq.plugins.jbossas5.util.ConversionUtils;
import org.rhq.plugins.jbossas5.util.DebugUtils;

/**
 * @author Ian Springer
 */
public abstract class AbstractManagedComponent implements ConfigurationFacet {
    private ResourceContext resourceContext;

    public void start(ResourceContext resourceContext) throws Exception {
        this.resourceContext = resourceContext;
    }

    public void stop() {
        return;
    }
    
    // ConfigurationComponent Implementation  --------------------------------------------

    public Configuration loadResourceConfiguration() {
        Map<String, ManagedProperty> managedProperties;
        try {
            managedProperties = getManagedProperties();
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to load ManagedProperties.", e);
        }
        Map<String, PropertySimple> customProps =
                ResourceComponentUtils.getCustomProperties(this.resourceContext.getPluginConfiguration());
        if (getLog().isDebugEnabled()) getLog().debug("*** AFTER LOAD:\n"
                + DebugUtils.convertPropertiesToString(managedProperties));
        @SuppressWarnings({"UnnecessaryLocalVariable"})
        Configuration resourceConfig = ConversionUtils.convertManagedObjectToConfiguration(managedProperties,
                customProps, this.resourceContext.getResourceType());
        return resourceConfig;
    }

    public void updateResourceConfiguration(ConfigurationUpdateReport configurationUpdateReport)
    {
        Configuration resourceConfig = configurationUpdateReport.getConfiguration();
        Configuration pluginConfig = this.resourceContext.getPluginConfiguration();
        try
        {
            Map<String, ManagedProperty> managedProperties = getManagedProperties();
            Map<String, PropertySimple> customProps = ResourceComponentUtils.getCustomProperties(pluginConfig);
            if (getLog().isDebugEnabled()) getLog().debug("*** BEFORE UPDATE:\n"
                    + DebugUtils.convertPropertiesToString(managedProperties));
            ConversionUtils.convertConfigurationToManagedProperties(managedProperties, resourceConfig,
                    this.resourceContext.getResourceType(), customProps);
            if (getLog().isDebugEnabled()) getLog().debug("*** AFTER UPDATE:\n"
                    + DebugUtils.convertPropertiesToString(managedProperties));
            updateComponent();
            configurationUpdateReport.setStatus(ConfigurationUpdateStatus.SUCCESS);
        }
        catch (Exception e)
        {
            configurationUpdateReport.setStatus(ConfigurationUpdateStatus.FAILURE);
            configurationUpdateReport.setErrorMessageFromThrowable(e);
        }
    }

    protected abstract Map<String, ManagedProperty> getManagedProperties() throws Exception;

    protected abstract void updateComponent() throws Exception;

    protected abstract Log getLog();

    protected ResourceContext getResourceContext() {
        return resourceContext;
    }
}
