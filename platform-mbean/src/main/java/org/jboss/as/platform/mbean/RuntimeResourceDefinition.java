/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.controller.registry.AttributeAccess.Flag.COUNTER_METRIC;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.RUNTIME_PATH;

import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.global.ReadResourceHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
class RuntimeResourceDefinition extends SimpleResourceDefinition {


    private static AttributeDefinition UPTIME = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.UPTIME, ModelType.LONG, false)
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setFlags(COUNTER_METRIC)
            .build();

    private static AttributeDefinition START_TIME = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.START_TIME, ModelType.LONG, false)
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    //todo convert to proper list! new StringListAttributeDefinition.Builder(PlatformMBeanConstants.SYSTEM_PROPERTIES)
     private static AttributeDefinition SYSTEM_PROPERTIES = new SimpleMapAttributeDefinition.Builder(PlatformMBeanConstants.SYSTEM_PROPERTIES,true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SYSTEM_PROPERTY)
            .build();

    private static AttributeDefinition INPUT_ARGUMENTS = new StringListAttributeDefinition.Builder(PlatformMBeanConstants.INPUT_ARGUMENTS)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.JVM)
            .setRequired(false)
            .build();

    private static AttributeDefinition VM_NAME = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.VM_NAME, ModelType.STRING, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static AttributeDefinition VM_VENDOR = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.VM_VENDOR, ModelType.STRING, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static AttributeDefinition VM_VERSION = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.VM_VERSION, ModelType.STRING, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static AttributeDefinition SPEC_NAME = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.SPEC_NAME, ModelType.STRING, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static AttributeDefinition SPEC_VENDOR = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.SPEC_VENDOR, ModelType.STRING, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static AttributeDefinition SPEC_VERSION = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.SPEC_VERSION, ModelType.STRING, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static AttributeDefinition MANAGEMENT_SPEC_VERSION = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.MANAGEMENT_SPEC_VERSION, ModelType.STRING, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static AttributeDefinition CLASS_PATH = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.CLASS_PATH, ModelType.STRING, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.JVM)
            .build();
    private static AttributeDefinition LIBRARY_PATH = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.LIBRARY_PATH, ModelType.STRING, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.JVM)
            .build();
    private static AttributeDefinition BOOT_CLASS_PATH_SUPPORTED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.BOOT_CLASS_PATH_SUPPORTED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.JVM)
            .build();
    private static AttributeDefinition BOOT_CLASS_PATH = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.BOOT_CLASS_PATH, ModelType.STRING, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.JVM)
            .build();
    private static AttributeDefinition PID = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.PID, ModelType.LONG, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static final List<AttributeDefinition> METRICS = Arrays.asList(
            UPTIME
    );
    private static final List<AttributeDefinition> READ_ATTRIBUTES = Arrays.asList(
            PlatformMBeanConstants.NAME,
            VM_NAME,
            VM_VENDOR,
            VM_VERSION,
            SPEC_NAME,
            SPEC_VENDOR,
            SPEC_VERSION,
            MANAGEMENT_SPEC_VERSION,
            CLASS_PATH,
            LIBRARY_PATH,
            BOOT_CLASS_PATH_SUPPORTED,
            BOOT_CLASS_PATH,
            INPUT_ARGUMENTS,
            START_TIME,
            SYSTEM_PROPERTIES,
            PID
    );

    public static final List<String> RUNTIME_READ_ATTRIBUTES = Arrays.asList(
            PlatformMBeanConstants.NAME.getName(),
            VM_NAME.getName(),
            VM_VENDOR.getName(),
            VM_VERSION.getName(),
            SPEC_NAME.getName(),
            SPEC_VENDOR.getName(),
            SPEC_VERSION.getName(),
            MANAGEMENT_SPEC_VERSION.getName(),
            CLASS_PATH.getName(),
            LIBRARY_PATH.getName(),
            BOOT_CLASS_PATH_SUPPORTED.getName(),
            BOOT_CLASS_PATH.getName(),
            INPUT_ARGUMENTS.getName(),
            START_TIME.getName(),
            SYSTEM_PROPERTIES.getName(),
            PID.getName()
    );
    public static final List<String> RUNTIME_METRICS = Arrays.asList(
            UPTIME.getName()
    );

    static final RuntimeResourceDefinition INSTANCE = new RuntimeResourceDefinition();

    private RuntimeResourceDefinition() {
        super(new Parameters(RUNTIME_PATH,
                PlatformMBeanUtil.getResolver(PlatformMBeanConstants.RUNTIME)).setRuntime());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        super.registerAttributes(registration);
        registration.registerReadOnlyAttribute(PlatformMBeanConstants.OBJECT_NAME, RuntimeMXBeanAttributeHandler.INSTANCE);

        for (AttributeDefinition attribute : READ_ATTRIBUTES) {
            registration.registerReadOnlyAttribute(attribute, RuntimeMXBeanAttributeHandler.INSTANCE);
        }

        for (AttributeDefinition attribute : METRICS) {
            registration.registerMetric(attribute, RuntimeMXBeanAttributeHandler.INSTANCE);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(ReadResourceHandler.DEFINITION, RuntimeMXBeanReadResourceHandler.INSTANCE);
    }
}

