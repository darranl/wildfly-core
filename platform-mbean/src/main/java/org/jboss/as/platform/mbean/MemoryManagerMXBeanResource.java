/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.PlatformMBeanConstants.MEMORY_MANAGER_TYPE;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.TYPE;
import static org.jboss.as.platform.mbean.PlatformMBeanUtil.escapeMBeanName;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * Resource impl for the {@link java.lang.management.MemoryManagerMXBean} parent resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class MemoryManagerMXBeanResource extends AbstractPlatformMBeanResource {

    MemoryManagerMXBeanResource() {
        super(PlatformMBeanConstants.MEMORY_MANAGER_PATH);
    }

    @Override
    ResourceEntry getChildEntry(String name) {
        for (MemoryManagerMXBean mbean : ManagementFactory.getMemoryManagerMXBeans()) {
            if (name.equals(escapeMBeanName(mbean.getName())) && mbean.getObjectName().getKeyProperty(TYPE).equals(MEMORY_MANAGER_TYPE)) {
                return new LeafPlatformMBeanResource(PathElement.pathElement(ModelDescriptionConstants.NAME, name));
            }
        }
        return null;
    }

    @Override
    Set<String> getChildrenNames() {
        final Set<String> result = new LinkedHashSet<String>();
        for (MemoryManagerMXBean mbean : ManagementFactory.getMemoryManagerMXBeans()) {
            if(mbean.getObjectName().getKeyProperty(TYPE).equals(MEMORY_MANAGER_TYPE)) {
                result.add(escapeMBeanName(mbean.getName()));
            }
        }
        return result;
    }

    @Override
    public Set<String> getChildTypes() {
        return Collections.singleton(ModelDescriptionConstants.NAME);
    }
}
