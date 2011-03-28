/*
 * RHQ Management Platform
 * Copyright (C) 2005-2011 Red Hat, Inc.
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
package org.rhq.modules.plugins.jbossas7;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.pluginapi.inventory.DiscoveredResourceDetails;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryComponent;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryContext;
import org.rhq.modules.plugins.jbossas7.json.Subsystem;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

/**
 * Discover subsystems
 *
 * @author Heiko W. Rupp
 */
@SuppressWarnings("unused")
public class SubsystemDiscovery implements ResourceDiscoveryComponent<BaseComponent> {

    private final Log log = LogFactory.getLog(this.getClass());

    public Set<DiscoveredResourceDetails> discoverResources(ResourceDiscoveryContext<BaseComponent> context)
            throws Exception {

        Set<DiscoveredResourceDetails> details = new HashSet<DiscoveredResourceDetails>(1);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        mapper.configure(DeserializationConfig.Feature.READ_ENUMS_USING_TO_STRING,true);

        BaseComponent parentComponent = context.getParentResourceComponent();
        ASConnection connection = parentComponent.getASConnection();


        Configuration config = context.getDefaultPluginConfiguration();
        String cpath = config.getSimpleValue("path", null);
        boolean recursive = false;

        String parentPath = parentComponent.getPath();

        String path;
        if (cpath.endsWith("/*")) {
            path = cpath.substring(0,cpath.length()-2);
            recursive = true;
        }
        else
            path = cpath;

        if (parentPath!=null && !parentPath.isEmpty()) {
            if (parentPath.endsWith("/") || path.startsWith("/"))
                path = parentPath + path;
            else
                path = parentPath + "/" + path;
        }
        System.out.println("total path: [" + path + "]");


        JsonNode json = connection.getLevelData(path,recursive, false);
        if (!connection.isErrorReply(json)) {
            if (recursive) {
                int i = path.lastIndexOf("/");
                String subPath = path.substring(i+1);

                JsonNode subNode = json.findPath(subPath);

                Map<String,Subsystem> subsystemMap = mapper.readValue(subNode,new TypeReference<Map<String,Subsystem>>() {});

                for (Map.Entry<String,Subsystem> entry: subsystemMap.entrySet()) {

                    String key = entry.getKey();
                    Subsystem subsystem = entry.getValue();
                    String newPath = cpath.replaceAll("\\*",key);
                    config.getSimple("path").setStringValue(newPath);

                    String resKey = context.getParentResourceContext().getResourceKey() + "/" + key;
                    String name = resKey.substring(resKey.lastIndexOf("/") + 1);


                    DiscoveredResourceDetails detail = new DiscoveredResourceDetails(
                            context.getResourceType(), // DataType
                            path + "/" + key, // Key
                            name, // Name
                            null, // Version
                            subsystem.description, // Description
                            config,
                            null);
                    details.add(detail);
                }

            }
            else {


                String resKey = path;
                String name = resKey.substring(resKey.lastIndexOf("/") + 1);
                config.getSimple("path").setStringValue(path);


                DiscoveredResourceDetails detail = new DiscoveredResourceDetails(
                        context.getResourceType(), // DataType
                        path, // Key
                        name, // Name
                        null, // Version
                        path, // Description
                        config,
                        null);
                details.add(detail);
            }

            return details;
        }

        return Collections.emptySet();
    }

}
