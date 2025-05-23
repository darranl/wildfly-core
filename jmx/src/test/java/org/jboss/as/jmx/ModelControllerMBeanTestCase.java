/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.server.services.net.SocketBindingResourceDefinition.SOCKET_BINDING_CAPABILITY;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.Attribute;
import javax.management.AttributeChangeNotification;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.RuntimeOperationsException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanOperationInfo;
import javax.management.openmbean.OpenMBeanParameterInfo;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.ExtensionRegistryType;
import org.jboss.as.controller.operations.common.ResolveExpressionHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.jmx.model.ModelControllerMBeanHelper;
import org.jboss.as.remoting.EndpointService;
import org.jboss.as.remoting.Protocol;
import org.jboss.as.remoting.RemotingServices;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.ControllerInitializer;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.staxmapper.XMLMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

/**
 *
 * This test modifies the {@link java.lang.management.ManagementFactory#getPlatformMBeanServer()} by setting the
 * @{code javax.management.builder.initial} system property.
 *
 * The jmx module runs its test target in Maven by always forking the JVM (and not reuse one) to ensure that the
 * platform mbean server is not modified outside of this test case.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ModelControllerMBeanTestCase extends AbstractSubsystemTest {

    private static final int JMX_PORT = 27258;

    private static final String LEGACY_DOMAIN = "jboss.resolved";
    private static final ObjectName LEGACY_ROOT_NAME = ModelControllerMBeanHelper.createRootObjectName(LEGACY_DOMAIN);
    private static final ObjectName LEGACY_INTERFACE_NAME = createObjectName(LEGACY_DOMAIN + ":interface=test-interface");
    private static final ObjectName LEGACY_SOCKET_BINDING_GROUP_NAME = createObjectName(LEGACY_DOMAIN + ":socket-binding-group=test-socket-binding-group");
    private static final ObjectName LEGACY_SERVER_SOCKET_BINDING_NAME = createObjectName(LEGACY_DOMAIN + ":socket-binding-group=test-socket-binding-group,socket-binding=server");
    private static final ObjectName LEGACY_SERVER_SOCKET_BINDING_NAME_2 = createObjectName(LEGACY_DOMAIN + ":socket-binding=server,socket-binding-group=test-socket-binding-group");
    private static final ObjectName LEGACY_SUBSYSTEM_NAME = createObjectName(LEGACY_DOMAIN + ":subsystem=jmx");
    private static final ObjectName LEGACY_BAD_NAME = createObjectName(LEGACY_DOMAIN + ":type=bad");

    private static final String EXPR_DOMAIN = "jboss.as.expr";
    private static final ObjectName EXPR_ROOT_NAME = ModelControllerMBeanHelper.createRootObjectName(EXPR_DOMAIN);
    private static final ObjectName EXPR_INTERFACE_NAME = createObjectName(EXPR_DOMAIN + ":interface=test-interface");
    private static final ObjectName EXPR_SOCKET_BINDING_GROUP_NAME = createObjectName(EXPR_DOMAIN + ":socket-binding-group=test-socket-binding-group");
    private static final ObjectName EXPR_SERVER_SOCKET_BINDING_NAME = createObjectName(EXPR_DOMAIN + ":socket-binding-group=test-socket-binding-group,socket-binding=server");
    private static final ObjectName EXPR_SERVER_SOCKET_BINDING_NAME_2 = createObjectName(EXPR_DOMAIN + ":socket-binding=server,socket-binding-group=test-socket-binding-group");
    private static final ObjectName EXPR_SUBSYSTEM_NAME = createObjectName(EXPR_DOMAIN + ":subsystem=jmx");
    private static final ObjectName EXPR_BAD_NAME = createObjectName(LEGACY_DOMAIN + ":type=bad");
    private static final QueryExp   PORT_QUERY_EXP = Query.eq(Query.attr("port"), Query.value(JMX_PORT));
    private static final QueryExp   PORT_STRING_QUERY_EXP = Query.eq(Query.attr("port"), Query.value(Integer.toString(JMX_PORT)));
    private static final QueryExp   DEFAULT_INTERFACE_QUERY_EXP = Query.eq(Query.attr("defaultInterface"), Query.value("test-interface"));

    private JMXConnector jmxConnector;
    private KernelServices kernelServices;

    public ModelControllerMBeanTestCase() {
        super(JMXExtension.SUBSYSTEM_NAME, new JMXExtension());
    }

    @BeforeClass
    public static void beforeClass() {
        System.setProperty("javax.management.builder.initial", PluggableMBeanServerBuilder.class.getName());
    }

    @Override
    @After
    public void cleanup() throws Exception {
        super.cleanup();
        IoUtils.safeClose(jmxConnector);
        jmxConnector = null;
        kernelServices = null;
    }

    @Test
    public void testExposedMBeans() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new BaseAdditionalInitialization(ProcessType.STANDALONE_SERVER));

        int count = connection.getMBeanCount();
        checkQueryMBeans(connection, count, null);
        checkQueryMBeans(connection, count, new ObjectName("*:*"));


        Set<ObjectInstance> filteredInstances = connection.queryMBeans(createObjectName(LEGACY_DOMAIN + ":socket-binding-group=*,*"),
                null);
        Set<ObjectName> filteredNames = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":socket-binding-group=*,*"), null);
        Assert.assertEquals(2, filteredInstances.size());
        Assert.assertEquals(2, filteredNames.size());
        checkSameMBeans(filteredInstances, filteredNames);
        assertContainsNames(filteredNames, LEGACY_SOCKET_BINDING_GROUP_NAME, LEGACY_SERVER_SOCKET_BINDING_NAME);

        filteredInstances = connection.queryMBeans(createObjectName(EXPR_DOMAIN + ":socket-binding-group=*,*"),
                null);
        filteredNames = connection.queryNames(createObjectName(EXPR_DOMAIN + ":socket-binding-group=*,*"), null);
        Assert.assertEquals(2, filteredInstances.size());
        Assert.assertEquals(2, filteredNames.size());
        checkSameMBeans(filteredInstances, filteredNames);
        assertContainsNames(filteredNames, EXPR_SOCKET_BINDING_GROUP_NAME, EXPR_SERVER_SOCKET_BINDING_NAME);

        // WFCORE-1716 Test with a property list pattern where the non-wildcard keys are for items later in the address
        filteredInstances = connection.queryMBeans(createObjectName(LEGACY_DOMAIN + ":socket-binding=*,*"),
        null);
        filteredNames = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":socket-binding=*,*"), null);
        Assert.assertEquals(1, filteredInstances.size());
        Assert.assertEquals(1, filteredNames.size());
        checkSameMBeans(filteredInstances, filteredNames);
        assertContainsNames(filteredNames, LEGACY_SERVER_SOCKET_BINDING_NAME);

        // WFCORE-1257 -- Test with QueryExp

        // First a numeric query (port) = (12345)
        filteredInstances = connection.queryMBeans(createObjectName(LEGACY_DOMAIN + ":socket-binding-group=*,*"),
                PORT_QUERY_EXP);
        filteredNames = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":socket-binding-group=*,*"), PORT_QUERY_EXP);
        Assert.assertEquals(1, filteredInstances.size());
        Assert.assertEquals(1, filteredNames.size());
        checkSameMBeans(filteredInstances, filteredNames);
        assertContainsNames(filteredNames, LEGACY_SERVER_SOCKET_BINDING_NAME);
        // Doesn't match on jboss.as.expr as the value is a string
        filteredInstances = connection.queryMBeans(createObjectName(EXPR_DOMAIN + ":socket-binding-group=*,*"),
                PORT_QUERY_EXP);
        filteredNames = connection.queryNames(createObjectName(EXPR_DOMAIN + ":socket-binding-group=*,*"), PORT_QUERY_EXP);
        Assert.assertEquals(0, filteredInstances.size());
        Assert.assertEquals(0, filteredNames.size());

        // Next a port string query (port) = ("12345")
        // Doesn't match on jboss.as as the value is an int
        filteredInstances = connection.queryMBeans(createObjectName(LEGACY_DOMAIN + ":socket-binding-group=*,*"),
                PORT_STRING_QUERY_EXP);
        filteredNames = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":socket-binding-group=*,*"), PORT_STRING_QUERY_EXP);
        Assert.assertEquals(0, filteredInstances.size());
        Assert.assertEquals(0, filteredNames.size());
        // Does match on jboss.as.expr as the value is a string
        filteredInstances = connection.queryMBeans(createObjectName(EXPR_DOMAIN + ":socket-binding-group=*,*"),
                PORT_STRING_QUERY_EXP);
        filteredNames = connection.queryNames(createObjectName(EXPR_DOMAIN + ":socket-binding-group=*,*"), PORT_STRING_QUERY_EXP);
        Assert.assertEquals(1, filteredInstances.size());
        Assert.assertEquals(1, filteredNames.size());
        checkSameMBeans(filteredInstances, filteredNames);
        assertContainsNames(filteredNames, EXPR_SERVER_SOCKET_BINDING_NAME);

        // Next a straight string query (defaultInterface) = ("test-interface")
        // Note this also checks a bit on the default-interface -> defaultInterface camel case handling
        filteredInstances = connection.queryMBeans(createObjectName(LEGACY_DOMAIN + ":socket-binding-group=*,*"),
                DEFAULT_INTERFACE_QUERY_EXP);
        filteredNames = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":socket-binding-group=*,*"),
                DEFAULT_INTERFACE_QUERY_EXP);
        Assert.assertEquals(1, filteredInstances.size());
        Assert.assertEquals(1, filteredNames.size());
        checkSameMBeans(filteredInstances, filteredNames);
        assertContainsNames(filteredNames, LEGACY_SOCKET_BINDING_GROUP_NAME);

        filteredInstances = connection.queryMBeans(createObjectName(EXPR_DOMAIN + ":socket-binding-group=*,*"),
                DEFAULT_INTERFACE_QUERY_EXP);
        filteredNames = connection.queryNames(createObjectName(EXPR_DOMAIN + ":socket-binding-group=*,*"), DEFAULT_INTERFACE_QUERY_EXP);
        Assert.assertEquals(1, filteredInstances.size());
        Assert.assertEquals(1, filteredNames.size());
        checkSameMBeans(filteredInstances, filteredNames);
        assertContainsNames(filteredNames, EXPR_SOCKET_BINDING_GROUP_NAME);
    }

    private void checkQueryMBeans(MBeanServerConnection connection, int count, ObjectName filter) throws Exception {
        Set<ObjectInstance> instances = connection.queryMBeans(filter, null);
        Set<ObjectName> objectNames = connection.queryNames(filter, null);
        Assert.assertEquals(count, instances.size());
        Assert.assertEquals(count, objectNames.size());

        checkSameMBeans(instances, objectNames);

        final ObjectName[] names = {LEGACY_ROOT_NAME, LEGACY_INTERFACE_NAME, LEGACY_SOCKET_BINDING_GROUP_NAME, LEGACY_SERVER_SOCKET_BINDING_NAME, LEGACY_SUBSYSTEM_NAME,
                EXPR_ROOT_NAME, EXPR_INTERFACE_NAME, EXPR_SOCKET_BINDING_GROUP_NAME, EXPR_SERVER_SOCKET_BINDING_NAME, EXPR_SUBSYSTEM_NAME};
        assertContainsNames(objectNames, names);

        for (ObjectName name : names) {
            checkQuerySingleMBean(connection, name);
        }
    }

    private void checkQuerySingleMBean(MBeanServerConnection connection, ObjectName filter) throws Exception {
        Set<ObjectInstance> instances = connection.queryMBeans(filter, null);
        Set<ObjectName> objectNames = connection.queryNames(filter, null);
        Assert.assertEquals("Expected 1 for " + filter, 1, instances.size());
        Assert.assertEquals("Expected 1 for " + filter, 1, objectNames.size());
        Assert.assertEquals(filter, instances.iterator().next().getObjectName());
        Assert.assertEquals(filter, objectNames.iterator().next());
    }

    @Test
    public void testGetObjectInstance() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new BaseAdditionalInitialization(ProcessType.STANDALONE_SERVER));

        Assert.assertNotNull(connection.getObjectInstance(LEGACY_ROOT_NAME));
        Assert.assertEquals(LEGACY_ROOT_NAME, connection.getObjectInstance(LEGACY_ROOT_NAME).getObjectName());
        Assert.assertNotNull(connection.getObjectInstance(LEGACY_INTERFACE_NAME));
        Assert.assertNotNull(connection.getObjectInstance(LEGACY_SERVER_SOCKET_BINDING_NAME));
        Assert.assertNotNull(connection.getObjectInstance(LEGACY_SERVER_SOCKET_BINDING_NAME_2));
        try {
            connection.getObjectInstance(LEGACY_BAD_NAME);
            Assert.fail();
        } catch (InstanceNotFoundException expected) {
            //expected
        }

        Assert.assertNotNull(connection.getObjectInstance(EXPR_ROOT_NAME));
        Assert.assertEquals(EXPR_ROOT_NAME, connection.getObjectInstance(EXPR_ROOT_NAME).getObjectName());
        Assert.assertNotNull(connection.getObjectInstance(EXPR_INTERFACE_NAME));
        Assert.assertNotNull(connection.getObjectInstance(EXPR_SERVER_SOCKET_BINDING_NAME));
        Assert.assertNotNull(connection.getObjectInstance(EXPR_SERVER_SOCKET_BINDING_NAME_2));
        try {
            connection.getObjectInstance(EXPR_BAD_NAME);
            Assert.fail();
        } catch (InstanceNotFoundException expected) {
            //expected
        }
    }

    @Test
    public void testGetMBeanInfoStandalone() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new TestExtension()));

        MBeanInfo info = connection.getMBeanInfo(LEGACY_ROOT_NAME);
        Assert.assertNotNull(info);

        //Make sure all occurrences of "-" have gone
        for (MBeanAttributeInfo attr : info.getAttributes()) {
            Assert.assertFalse(attr.getName().contains("-"));
        }
        for (MBeanOperationInfo op : info.getOperations()) {
            Assert.assertFalse(op.getName().contains("-"));
            for (MBeanParameterInfo param : op.getSignature()) {
                Assert.assertFalse(param.getName().contains("-"));
            }
        }

        // Make sure that the description gets set for things using resource
        // bundles
        info = connection.getMBeanInfo(createObjectName(LEGACY_DOMAIN + ":subsystem=jmx"));
        Assert.assertNotNull(info);
        Assert.assertEquals(JMXExtension.getResourceDescriptionResolver("").getResourceBundle(Locale.getDefault())
                .getString(CommonAttributes.JMX), info.getDescription());

        info = connection.getMBeanInfo(createObjectName(LEGACY_DOMAIN + ":subsystem=test"));
        Assert.assertNotNull(info);
        Assert.assertEquals("description", info.getDescription());

        checkMBeanInfoAttributes(info, true, false);

        MBeanOperationInfo[] operations = info.getOperations();
        Assert.assertEquals(4, operations.length);

        OpenMBeanOperationInfo op = findOperation(operations, ModelControllerResourceDefinition.VoidOperationNoParams.OPERATION_JMX_NAME);
        Assert.assertEquals(ModelControllerResourceDefinition.VoidOperationNoParams.OPERATION_JMX_NAME, op.getName());
        Assert.assertEquals("void-no-params", op.getDescription());
        Assert.assertEquals(0, op.getSignature().length);
        Assert.assertEquals(Void.class.getName(), op.getReturnType());

        op = findOperation(operations, ModelControllerResourceDefinition.IntOperationWithParams.OPERATION_JMX_NAME);
        Assert.assertEquals(ModelControllerResourceDefinition.IntOperationWithParams.OPERATION_JMX_NAME, op.getName());
        Assert.assertEquals("int-with-params", op.getDescription());
        Assert.assertEquals(String.class.getName(), op.getReturnType());
        Assert.assertEquals(5, op.getSignature().length);

        Assert.assertEquals("param1", op.getSignature()[0].getName());
        Assert.assertEquals("int-with-params-param1", op.getSignature()[0].getDescription());
        Assert.assertEquals(Long.class.getName(), op.getSignature()[0].getType());

        Assert.assertEquals("param2", op.getSignature()[1].getName());
        Assert.assertEquals("int-with-params-param2", op.getSignature()[1].getDescription());
        Assert.assertEquals(String[].class.getName(), op.getSignature()[1].getType());

        Assert.assertEquals("param3", op.getSignature()[2].getName());
        Assert.assertEquals("int-with-params-param3", op.getSignature()[2].getDescription());
        Assert.assertEquals(TabularData.class.getName(), op.getSignature()[2].getType());
        assertMapType(assertCast(OpenMBeanParameterInfo.class, op.getSignature()[2]).getOpenType(), SimpleType.STRING, SimpleType.INTEGER);

        Assert.assertEquals("param4", op.getSignature()[3].getName());
        Assert.assertEquals("int-with-params-param4", op.getSignature()[3].getDescription());
        Assert.assertEquals(Integer.class.getName(), op.getSignature()[3].getType());
        OpenMBeanParameterInfo parameterInfo = assertCast(OpenMBeanParameterInfo.class, op.getSignature()[3]);
        Assert.assertEquals(6, parameterInfo.getDefaultValue());
        Assert.assertEquals(5, parameterInfo.getMinValue());
        Assert.assertEquals(10, parameterInfo.getMaxValue());

        Assert.assertEquals("param5", op.getSignature()[4].getName());
        Assert.assertEquals("int-with-params-param5", op.getSignature()[4].getDescription());
        Assert.assertEquals(Integer.class.getName(), op.getSignature()[4].getType());
        parameterInfo = assertCast(OpenMBeanParameterInfo.class, op.getSignature()[4]);
        Assert.assertNull(parameterInfo.getDefaultValue());
        Assert.assertEquals(new HashSet<Object>(Arrays.asList(3, 5, 7)), parameterInfo.getLegalValues()); //todo add support for allowed values to AD

        op = findOperation(operations, ModelControllerResourceDefinition.ComplexOperation.OPERATION_NAME);
        Assert.assertEquals(ModelControllerResourceDefinition.ComplexOperation.OPERATION_NAME, op.getName());
        Assert.assertEquals("complex", op.getDescription());
        checkComplexTypeInfo(assertCast(CompositeType.class, op.getReturnOpenType()), false, "complex.");
        Assert.assertEquals(1, op.getSignature().length);
        checkComplexTypeInfo(assertCast(CompositeType.class, assertCast(OpenMBeanParameterInfo.class, op.getSignature()[0]).getOpenType()), false, "param1.");

        MBeanNotificationInfo[] notifications = info.getNotifications();
        Set<String> notificationTypes = getNotificationTypes(notifications);
        Assert.assertEquals(1, notificationTypes.size());
        Assert.assertTrue(notificationTypes.contains(AttributeChangeNotification.ATTRIBUTE_CHANGE));
    }

    private Set<String> getNotificationTypes(MBeanNotificationInfo[] notifications) {
        Set<String> notificationTypes = new HashSet<String>();
        for (MBeanNotificationInfo notification : notifications) {
            for (String notificationType : notification.getNotifTypes()) {
                    notificationTypes.add(notificationType);
            }
        }
        return notificationTypes;
    }

    @Test
    public void testGetMBeanInfoDomain() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.DOMAIN_SERVER, new TestExtension()));

        MBeanInfo info = connection.getMBeanInfo(LEGACY_ROOT_NAME);
        Assert.assertNotNull(info);

        //Make sure all occurrences of "-" have gone
        for (MBeanAttributeInfo attr : info.getAttributes()) {
            Assert.assertFalse(attr.getName().contains("-"));
        }
        for (MBeanOperationInfo op : info.getOperations()) {
            Assert.assertFalse(op.getName().contains("-"));
            for (MBeanParameterInfo param : op.getSignature()) {
                Assert.assertFalse(param.getName().contains("-"));
            }
        }

        // Make sure that the description gets set for things using resource
        // bundles
        info = connection.getMBeanInfo(createObjectName(LEGACY_DOMAIN + ":subsystem=jmx"));
        Assert.assertNotNull(info);
        Assert.assertEquals(JMXExtension.getResourceDescriptionResolver("").getResourceBundle(Locale.getDefault())
                .getString(CommonAttributes.JMX), info.getDescription());

        info = connection.getMBeanInfo(createObjectName(LEGACY_DOMAIN + ":subsystem=test"));
        Assert.assertNotNull(info);
        Assert.assertEquals("description", info.getDescription());

        //All attributes should be read-only
        checkMBeanInfoAttributes(info, false, false);


        MBeanOperationInfo[] operations = info.getOperations();
        Assert.assertEquals(2, operations.length);

        OpenMBeanOperationInfo op = findOperation(operations, ModelControllerResourceDefinition.VoidOperationNoParams.OPERATION_JMX_NAME);
        Assert.assertEquals(ModelControllerResourceDefinition.VoidOperationNoParams.OPERATION_JMX_NAME, op.getName());
        Assert.assertEquals("void-no-params", op.getDescription());
        Assert.assertEquals(0, op.getSignature().length);
        Assert.assertEquals(Void.class.getName(), op.getReturnType());
    }

    @Test
    public void testGetMBeanInfoExpressionsStandalone() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new TestExtension(true)));

        MBeanInfo info = connection.getMBeanInfo(EXPR_ROOT_NAME);
        Assert.assertNotNull(info);

        //Make sure all occurrences of "-" have gone
        for (MBeanAttributeInfo attr : info.getAttributes()) {
            Assert.assertFalse(attr.getName().contains("-"));
        }
        for (MBeanOperationInfo op : info.getOperations()) {
            Assert.assertFalse(op.getName().contains("-"));
            for (MBeanParameterInfo param : op.getSignature()) {
                Assert.assertFalse(param.getName().contains("-"));
            }
        }

        // Make sure that the description gets set for things using resource
        // bundles
        info = connection.getMBeanInfo(createObjectName(EXPR_DOMAIN + ":subsystem=jmx"));
        Assert.assertNotNull(info);
        Assert.assertEquals(JMXExtension.getResourceDescriptionResolver("").getResourceBundle(Locale.getDefault())
                .getString(CommonAttributes.JMX), info.getDescription());

        info = connection.getMBeanInfo(createObjectName(EXPR_DOMAIN + ":subsystem=test"));
        Assert.assertNotNull(info);
        Assert.assertEquals("description", info.getDescription());

        checkMBeanInfoAttributes(info, true, true);

        MBeanOperationInfo[] operations = info.getOperations();
        Assert.assertEquals(4, operations.length);

        OpenMBeanOperationInfo op = findOperation(operations, ModelControllerResourceDefinition.VoidOperationNoParams.OPERATION_JMX_NAME);
        Assert.assertEquals(ModelControllerResourceDefinition.VoidOperationNoParams.OPERATION_JMX_NAME, op.getName());
        Assert.assertEquals("void-no-params", op.getDescription());
        Assert.assertEquals(0, op.getSignature().length);
        Assert.assertEquals(Void.class.getName(), op.getReturnType());

        op = findOperation(operations, ModelControllerResourceDefinition.IntOperationWithParams.OPERATION_JMX_NAME);
        Assert.assertEquals(ModelControllerResourceDefinition.IntOperationWithParams.OPERATION_JMX_NAME, op.getName());
        Assert.assertEquals("int-with-params", op.getDescription());
        Assert.assertEquals(String.class.getName(), op.getReturnType());
        Assert.assertEquals(5, op.getSignature().length);

        Assert.assertEquals("param1", op.getSignature()[0].getName());
        Assert.assertEquals("int-with-params-param1", op.getSignature()[0].getDescription());
        Assert.assertEquals(String.class.getName(), op.getSignature()[0].getType());

        Assert.assertEquals("param2", op.getSignature()[1].getName());
        Assert.assertEquals("int-with-params-param2", op.getSignature()[1].getDescription());
        Assert.assertEquals(String[].class.getName(), op.getSignature()[1].getType());

        Assert.assertEquals("param3", op.getSignature()[2].getName());
        Assert.assertEquals("int-with-params-param3", op.getSignature()[2].getDescription());
        Assert.assertEquals(TabularData.class.getName(), op.getSignature()[2].getType());
        assertMapType(assertCast(OpenMBeanParameterInfo.class, op.getSignature()[2]).getOpenType(), SimpleType.STRING, SimpleType.STRING);

        Assert.assertEquals("param4", op.getSignature()[3].getName());
        Assert.assertEquals("int-with-params-param4", op.getSignature()[3].getDescription());
        Assert.assertEquals(String.class.getName(), op.getSignature()[3].getType());
        OpenMBeanParameterInfo parameterInfo = assertCast(OpenMBeanParameterInfo.class, op.getSignature()[3]);
        Assert.assertNotNull(parameterInfo.getDefaultValue());
        Assert.assertNull(parameterInfo.getMinValue());
        Assert.assertNull(parameterInfo.getMaxValue());

        Assert.assertEquals("param5", op.getSignature()[4].getName());
        Assert.assertEquals("int-with-params-param5", op.getSignature()[4].getDescription());
        Assert.assertEquals(String.class.getName(), op.getSignature()[4].getType());
        parameterInfo = assertCast(OpenMBeanParameterInfo.class, op.getSignature()[4]);
        Assert.assertNull(parameterInfo.getDefaultValue());
        Assert.assertNotNull(parameterInfo.getLegalValues());
        Assert.assertNull(parameterInfo.getDefaultValue());

        op = findOperation(operations, ModelControllerResourceDefinition.ComplexOperation.OPERATION_NAME);
        Assert.assertEquals(ModelControllerResourceDefinition.ComplexOperation.OPERATION_NAME, op.getName());
        Assert.assertEquals("complex", op.getDescription());
        checkComplexTypeInfo(assertCast(CompositeType.class, op.getReturnOpenType()), true, "complex.");
        Assert.assertEquals(1, op.getSignature().length);
        checkComplexTypeInfo(assertCast(CompositeType.class, assertCast(OpenMBeanParameterInfo.class, op.getSignature()[0]).getOpenType()), true, "param1.");
    }


    private void checkMBeanInfoAttributes(MBeanInfo info, boolean writable, boolean expressions) {
        //All attributes should be read-only
        MBeanAttributeInfo[] attributes = info.getAttributes();
        Assert.assertEquals(14, attributes.length);
        Arrays.sort(attributes, new Comparator<MBeanAttributeInfo>() {
            @Override
            public int compare(MBeanAttributeInfo o1, MBeanAttributeInfo o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        assertAttributeDescription(attributes[0], "bigdec", expressions ? String.class.getName() : BigDecimal.class.getName(), "bigdec", true, writable);
        assertAttributeDescription(attributes[1], "bigint", expressions ? String.class.getName() : BigInteger.class.getName(), "bigint", true, writable);
        assertAttributeDescription(attributes[2], "boolean", expressions ? String.class.getName() : Boolean.class.getName(), "boolean", true, writable);
        assertAttributeDescription(attributes[3], "bytes", byte[].class.getName(), "bytes", true, writable);
        checkComplexTypeInfo(assertCast(CompositeType.class, assertCast(OpenMBeanAttributeInfo.class, attributes[4]).getOpenType()), expressions, "complex.");
        assertAttributeDescription(attributes[5], "double", expressions ? String.class.getName() : Double.class.getName(), "double", true, writable);
        assertAttributeDescription(attributes[6], "int", expressions ? String.class.getName() : Integer.class.getName(), "int", true, writable);
        assertAttributeDescription(attributes[7], "list", expressions ? String[].class.getName() : Integer[].class.getName(), "list", true, writable);
        assertAttributeDescription(attributes[8], "long", expressions ? String.class.getName() : Long.class.getName(), "long", true, writable);
        //type=OBJECT, value-type=a simple type -> a map
        assertAttributeDescription(attributes[9], "map", TabularData.class.getName(), "map", true, writable);
        assertMapType(assertCast(OpenMBeanAttributeInfo.class, attributes[9]).getOpenType(), SimpleType.STRING, expressions ? SimpleType.STRING : SimpleType.INTEGER);
        assertAttributeDescription(attributes[10], "roInt", expressions ? String.class.getName() : Integer.class.getName(), "ro-int", true, false);
        assertAttributeDescription(attributes[11], "string", expressions ? String.class.getName() : String.class.getName(), "string", true, writable);
        assertAttributeDescription(attributes[12], "type", String.class.getName(), "type", true, writable);
        assertAttributeDescription(attributes[13], "undefinedInt", expressions ? String.class.getName() : Integer.class.getName(), "undefined-int", true, writable);

    }

    @Test
    public void testReadWriteAttributeStandalone() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new TestExtension()));

        ObjectName name = createObjectName(LEGACY_DOMAIN + ":subsystem=test");
        checkAttributeValues(connection, name, 1, null, 2, BigInteger.valueOf(3), BigDecimal.valueOf(4), false, new byte[]{5, 6}, 7.0, "8",
                Collections.singletonList(9), 10, ModelType.INT, "key1", 11, "key2", 12);
        Assert.assertNull(connection.getAttribute(name, "complex"));


        try {
            connection.setAttribute(name, new Attribute("roInt", 101));
            Assert.fail("roInt not writable");
        } catch (Exception expected) {
            //expected
        }

        connection.setAttribute(name, new Attribute("int", 102));
        connection.setAttribute(name, new Attribute("undefinedInt", 103));
        connection.setAttribute(name, new Attribute("bigint", BigInteger.valueOf(104)));
        connection.setAttribute(name, new Attribute("bigdec", BigDecimal.valueOf(105)));
        connection.setAttribute(name, new Attribute("boolean", Boolean.TRUE));
        connection.setAttribute(name, new Attribute("bytes", new byte[]{106, 107}));
        connection.setAttribute(name, new Attribute("double", 108.0));
        connection.setAttribute(name, new Attribute("string", "109"));
        connection.setAttribute(name, new Attribute("list", new Integer[]{110}));
        connection.setAttribute(name, new Attribute("long", 111L));
        connection.setAttribute(name, new Attribute("type", ModelType.STRING.toString()));
        Map<String, Integer> map = new HashMap<String, Integer>();
        map.put("keyA", 112);
        map.put("keyB", 113);
        connection.setAttribute(name, new Attribute("map", map));
        MBeanInfo info = connection.getMBeanInfo(name);
        CompositeType complexType = assertCast(CompositeType.class, findAttribute(info.getAttributes(), "complex").getOpenType());
        connection.setAttribute(name, new Attribute("complex", createComplexData(connection, complexType, 1, BigDecimal.valueOf(2.0))));


        checkAttributeValues(connection, name, 1, 103, 102, BigInteger.valueOf(104), BigDecimal.valueOf(105), true, new byte[]{106, 107}, 108.0, "109",
                Collections.singletonList(110), 111, ModelType.STRING, "keyA", 112, "keyB", 113);
        CompositeData compositeData = assertCast(CompositeData.class, connection.getAttribute(name, "complex"));
        Assert.assertEquals(1, compositeData.get("int-value"));
        Assert.assertEquals(BigDecimal.valueOf(2.0), compositeData.get("bigdecimal-value"));
    }

    @Test
    public void testReadWriteAttributeDomain() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.DOMAIN_SERVER, new TestExtension()));


        ObjectName name = createObjectName(LEGACY_DOMAIN + ":subsystem=test");

        checkAttributeValues(connection, name, 1, null, 2, BigInteger.valueOf(3), BigDecimal.valueOf(4), false, new byte[]{5, 6}, 7.0, "8",
                Collections.singletonList(9), 10, ModelType.INT, "key1", 11, "key2", 12);
        Assert.assertNull(connection.getAttribute(name, "complex"));


        try {
            connection.setAttribute(name, new Attribute("roInt", 101));
            Assert.fail("roInt not writable");
        } catch (Exception expected) {
            //expected
        }
        try {
            connection.setAttribute(name, new Attribute("int", 102));
            Assert.fail("int not writable");
        } catch (Exception expected) {
            //expected
        }
        try {
            connection.setAttribute(name, new Attribute("undefinedInt", 103));
            Assert.fail("undefinedInt not writable");
        } catch (Exception expected) {
            //expected
        }
        try {
            //TODO BigInteger not working in current DMR version
            //connection.setAttribute(name, new Attribute("bigint", BigInteger.valueOf(104)));
            connection.setAttribute(name, new Attribute("bigdec", BigDecimal.valueOf(105)));
            Assert.fail("bigdec not writable");
        } catch (Exception expected) {
            //expected
        }
        try {
            connection.setAttribute(name, new Attribute("boolean", Boolean.TRUE));
            Assert.fail("boolean not writable");
        } catch (Exception expected) {
            //expected
        }
        try {
            connection.setAttribute(name, new Attribute("bytes", new byte[]{106, 107}));
            Assert.fail("bytes not writable");
        } catch (Exception expected) {
            //expected
        }
        try {
            connection.setAttribute(name, new Attribute("double", 108.0));
            Assert.fail("double not writable");
        } catch (Exception expected) {
            //expected
        }
        try {
            connection.setAttribute(name, new Attribute("string", "109"));
            Assert.fail("string not writable");
        } catch (Exception expected) {
            //expected
        }
        try {
            connection.setAttribute(name, new Attribute("list", new Integer[]{110}));
            Assert.fail("list not writable");
        } catch (Exception expected) {
            //expected
        }
        try {
            connection.setAttribute(name, new Attribute("long", 111L));
            Assert.fail("long not writable");
        } catch (Exception expected) {
            //expected
        }
        try {
            connection.setAttribute(name, new Attribute("type", ModelType.STRING.toString()));
            Assert.fail("type not writable");
        } catch (Exception expected) {
            //expected
        }
        try {
            Map<String, Integer> map = new HashMap<String, Integer>();
            map.put("keyA", 112);
            map.put("keyB", 113);
            connection.setAttribute(name, new Attribute("map", map));
            Assert.fail("map not writable");
        } catch (Exception expected) {
            //expected
        }

        MBeanInfo info = connection.getMBeanInfo(name);
        CompositeType complexType = assertCast(CompositeType.class, findAttribute(info.getAttributes(), "complex").getOpenType());
        CompositeData compositeData = createComplexData(connection, complexType, 1, BigDecimal.valueOf(2.0));
        try {
            connection.setAttribute(name, new Attribute("complex", compositeData));
            Assert.fail("Complex not writable");
        } catch (Exception expected) {
            //expected
        }

        checkAttributeValues(connection, name, 1, null, 2, BigInteger.valueOf(3), BigDecimal.valueOf(4), false, new byte[]{5, 6}, 7.0, "8",
                Collections.singletonList(9), 10, ModelType.INT, "key1", 11, "key2", 12);
        Assert.assertNull(connection.getAttribute(name, "complex"));
    }


    private void checkAttributeValues(MBeanServerConnection connection, ObjectName name,
                                      int roInt, Integer undefinedInt, int i, BigInteger bigInt, BigDecimal bigDecimal,
                                      boolean bool, byte[] bytes, double dbl, String s, List<Integer> list, long lng,
                                      ModelType type, String tblKey1, int tblValue1, String tblKey2, int tblValue2) throws Exception {
        Assert.assertEquals(roInt, assertCast(Integer.class, connection.getAttribute(name, "roInt")).intValue());
        if (undefinedInt == null) {
            Assert.assertNull(connection.getAttribute(name, "undefinedInt"));
        } else {
            Assert.assertEquals(undefinedInt, assertCast(Integer.class, connection.getAttribute(name, "undefinedInt")));
        }
        Assert.assertEquals(i, assertCast(Integer.class, connection.getAttribute(name, "int")).intValue());
        //TODO BigInteger not working in current DMR version
        //Assert.assertEquals(BigInteger.valueOf(3), assertCast(BigInteger.class, connection.getAttribute(name, "bigint")));
        Assert.assertEquals(bigDecimal, assertCast(BigDecimal.class, connection.getAttribute(name, "bigdec")));
        Assert.assertEquals(bool, assertCast(Boolean.class, connection.getAttribute(name, "boolean")));
        assertEqualByteArray(assertCast(byte[].class, connection.getAttribute(name, "bytes")), bytes);
        Assert.assertEquals(dbl, assertCast(Double.class, connection.getAttribute(name, "double")), 0.0d);
        Assert.assertEquals(s, assertCast(String.class, connection.getAttribute(name, "string")));

        Integer[] listValue = assertCast(Integer[].class, connection.getAttribute(name, "list"));
        Assert.assertEquals(list.size(), listValue.length);
        for (int ctr = 0; ctr < list.size(); ctr++) {
            Assert.assertEquals(list.get(ctr), listValue[ctr]);
        }
        Assert.assertEquals(lng, assertCast(Long.class, connection.getAttribute(name, "long")).longValue());
        Assert.assertEquals(type, ModelType.valueOf(assertCast(String.class, connection.getAttribute(name, "type"))));
        TabularData tabularData = assertCast(TabularData.class, connection.getAttribute(name, "map"));
        Assert.assertEquals(2, tabularData.size());
        Assert.assertEquals(tblValue1, assertCast(Integer.class, tabularData.get(new Object[]{tblKey1}).get("value")).intValue());
        Assert.assertEquals(tblValue2, assertCast(Integer.class, tabularData.get(new Object[]{tblKey2}).get("value")).intValue());
    }

    @Test
    public void testReadWriteAttributeExpressionsStandalone() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new TestExtension(true)));

        ObjectName name = createObjectName(EXPR_DOMAIN + ":subsystem=test");
        checkAttributeValues(connection, name, "1", null, "2", "3", "4", "false", new byte[]{5, 6}, "7.0", "8",
                Collections.singletonList("9"), "10", ModelType.INT.toString(), "key1", "11", "key2", "12");
        Assert.assertNull(connection.getAttribute(name, "complex"));


        try {
            connection.setAttribute(name, new Attribute("roInt", 101));
            Assert.fail("roInt not writable");
        } catch (Exception expected) {
            //expected
        }

        connection.setAttribute(name, new Attribute("int", "${should.not.exist!!!!!:102}"));
        connection.setAttribute(name, new Attribute("undefinedInt", "${should.not.exist!!!!!:103}"));
        connection.setAttribute(name, new Attribute("bigint", "${should.not.exist!!!!!:104}"));
        connection.setAttribute(name, new Attribute("bigdec", "${should.not.exist!!!!!:105}"));
        connection.setAttribute(name, new Attribute("boolean", "${should.not.exist!!!!!:true}"));
        connection.setAttribute(name, new Attribute("bytes", new byte[]{106, 107}));
        connection.setAttribute(name, new Attribute("double", "${should.not.exist!!!!!:108.0}"));
        connection.setAttribute(name, new Attribute("string", "${should.not.exist!!!!!:109}"));
        connection.setAttribute(name, new Attribute("list", new String[]{"${should.not.exist!!!!!:110}"}));
        connection.setAttribute(name, new Attribute("long", "${should.not.exist!!!!!:111}"));
        connection.setAttribute(name, new Attribute("type", "${should.not.exist!!!!!:STRING}"));
        Map<String, String> map = new HashMap<String, String>();
        map.put("keyA", "${should.not.exist!!!!!:112}");
        map.put("keyB", "${should.not.exist!!!!!:113}");
        connection.setAttribute(name, new Attribute("map", map));
        MBeanInfo info = connection.getMBeanInfo(name);
        CompositeType complexType = assertCast(CompositeType.class, findAttribute(info.getAttributes(), "complex").getOpenType());
        connection.setAttribute(name, new Attribute("complex", createComplexData(connection, complexType, "${should.not.exist!!!!!:1}", "${should.not.exist!!!!!:2.0}")));


        checkAttributeValues(connection, name, "1", "${should.not.exist!!!!!:103}", "${should.not.exist!!!!!:102}", "${should.not.exist!!!!!:104}", "${should.not.exist!!!!!:105}", "${should.not.exist!!!!!:true}",
                new byte[]{106, 107}, "${should.not.exist!!!!!:108.0}", "${should.not.exist!!!!!:109}",
                Collections.singletonList("${should.not.exist!!!!!:110}"), "${should.not.exist!!!!!:111}", "${should.not.exist!!!!!:STRING}", "keyA", "${should.not.exist!!!!!:112}", "keyB", "${should.not.exist!!!!!:113}");
        CompositeData compositeData = assertCast(CompositeData.class, connection.getAttribute(name, "complex"));
        Assert.assertEquals("${should.not.exist!!!!!:1}", compositeData.get("int-value"));
        Assert.assertEquals("${should.not.exist!!!!!:2.0}", compositeData.get("bigdecimal-value"));
    }

    private void checkAttributeValues(MBeanServerConnection connection, ObjectName name,
                                      String roInt, String undefinedInt, String i, String bigInt, String bigDecimal,
                                      String bool, byte[] bytes, String dbl, String s, List<String> list, String lng,
                                      String type, String tblKey1, String tblValue1, String tblKey2, String tblValue2) throws Exception {
        Assert.assertEquals(roInt, assertCast(String.class, connection.getAttribute(name, "roInt")));
        if (undefinedInt == null) {
            Assert.assertNull(connection.getAttribute(name, "undefinedInt"));
        } else {
            Assert.assertEquals(undefinedInt, assertCast(String.class, connection.getAttribute(name, "undefinedInt")));
        }
        Assert.assertEquals(i, assertCast(String.class, connection.getAttribute(name, "int")));
        Assert.assertEquals(bigInt, assertCast(String.class, connection.getAttribute(name, "bigint")));
        Assert.assertEquals(bigDecimal, assertCast(String.class, connection.getAttribute(name, "bigdec")));
        Assert.assertEquals(bool, assertCast(String.class, connection.getAttribute(name, "boolean")));
        assertEqualByteArray(bytes, assertCast(byte[].class, connection.getAttribute(name, "bytes")));
        Assert.assertEquals(dbl, assertCast(String.class, connection.getAttribute(name, "double")));
        Assert.assertEquals(s, assertCast(String.class, connection.getAttribute(name, "string")));

        String[] listValue = assertCast(String[].class, connection.getAttribute(name, "list"));
        Assert.assertEquals(list.size(), listValue.length);
        for (int ctr = 0; ctr < list.size(); ctr++) {
            Assert.assertEquals(list.get(ctr), listValue[ctr]);
        }
        Assert.assertEquals(lng, assertCast(String.class, connection.getAttribute(name, "long")));
        Assert.assertEquals(type, assertCast(String.class, connection.getAttribute(name, "type")));
        TabularData tabularData = assertCast(TabularData.class, connection.getAttribute(name, "map"));
        Assert.assertEquals(2, tabularData.size());
        Assert.assertEquals(tblValue1, assertCast(String.class, tabularData.get(new Object[]{tblKey1}).get("value")));
        Assert.assertEquals(tblValue2, assertCast(String.class, tabularData.get(new Object[]{tblKey2}).get("value")));
    }

    @Test
    public void testReadWriteAttributeListStandalone() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new TestExtension()));

        ObjectName name = createObjectName(LEGACY_DOMAIN + ":subsystem=test");
        String[] attrNames = new String[]{"roInt", "int", "bigint", "bigdec", "boolean", "bytes", "double", "string", "list", "long", "type"};
        AttributeList list = connection.getAttributes(name, attrNames);
        Assert.assertEquals(list.size(), attrNames.length);

        checkAttributeList(attrNames, list, 1, 2, BigInteger.valueOf(3), BigDecimal.valueOf(4), false, new byte[]{5, 6}, 7.0, "8",
                Collections.singletonList(9), 10, ModelType.INT);

        list = new AttributeList();
        list.add(new Attribute("int", 102));
        list.add(new Attribute("bigint", BigInteger.valueOf(103)));
        list.add(new Attribute("bigdec", BigDecimal.valueOf(104)));
        list.add(new Attribute("boolean", true));
        list.add(new Attribute("bytes", new byte[]{105, 106}));
        list.add(new Attribute("double", 107.0));
        list.add(new Attribute("string", "108"));
        list.add(new Attribute("list", new Integer[]{109}));
        list.add(new Attribute("long", 110L));
        list.add(new Attribute("type", ModelType.STRING.toString()));
        connection.setAttributes(name, list);

        list = connection.getAttributes(name, attrNames);
        checkAttributeList(attrNames, list, 1, 102, BigInteger.valueOf(103), BigDecimal.valueOf(104), true, new byte[]{105, 106}, 107.0, "108",
                Collections.singletonList(109), 110, ModelType.STRING);
    }

    @Test
    public void testReadWriteAttributeListDomain() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.DOMAIN_SERVER, new TestExtension()));

        ObjectName name = createObjectName(LEGACY_DOMAIN + ":subsystem=test");
        String[] attrNames = new String[]{"roInt", "int", "bigint", "bigdec", "boolean", "bytes", "double", "string", "list", "long", "type"};
        AttributeList list = connection.getAttributes(name, attrNames);
        Assert.assertEquals(list.size(), attrNames.length);

        checkAttributeList(attrNames, list, 1, 2, BigInteger.valueOf(3), BigDecimal.valueOf(4), false, new byte[]{5, 6}, 7.0, "8",
                Collections.singletonList(9), 10, ModelType.INT);

        list = new AttributeList();
        try {
            list.add(new Attribute("int", 102));
            //TODO BigInteger not working in current DMR version
            //list.add(new Attribute("bigint", BigInteger.valueOf(103)));
            list.add(new Attribute("bigdec", BigDecimal.valueOf(104)));
            list.add(new Attribute("boolean", true));
            list.add(new Attribute("bytes", new byte[]{105, 106}));
            list.add(new Attribute("double", 107.0));
            list.add(new Attribute("string", "108"));
            list.add(new Attribute("list", new Integer[]{109}));
            list.add(new Attribute("long", 110L));
            list.add(new Attribute("type", ModelType.STRING.toString()));
            connection.setAttributes(name, list);
            Assert.fail("Should not have been able to set attributes");
        } catch (Exception expected) {
            //expected
        }

        list = connection.getAttributes(name, attrNames);
        checkAttributeList(attrNames, list, 1, 2, BigInteger.valueOf(3), BigDecimal.valueOf(4), false, new byte[]{5, 6}, 7.0, "8",
                Collections.singletonList(9), 10, ModelType.INT);
    }

    private void checkAttributeList(String[] attrNames, AttributeList list, int roInt, int i, BigInteger bi, BigDecimal bd, boolean b,
                                    byte[] bytes, double d, String s, List<Integer> lst, long l, ModelType type) {
        Assert.assertEquals(list.size(), attrNames.length);

        Assert.assertEquals(roInt, assertGetFromList(Integer.class, list, "roInt").intValue());
        Assert.assertEquals(i, assertGetFromList(Integer.class, list, "int").intValue());
        Assert.assertEquals(bi, assertGetFromList(BigInteger.class, list, "bigint"));
        Assert.assertEquals(bd, assertGetFromList(BigDecimal.class, list, "bigdec"));
        Assert.assertEquals(b, assertGetFromList(Boolean.class, list, "boolean"));
        assertEqualByteArray(assertGetFromList(byte[].class, list, "bytes"), bytes);
        Assert.assertEquals(d, assertGetFromList(Double.class, list, "double"), 0.0d);
        Assert.assertEquals(s, assertGetFromList(String.class, list, "string"));
        Integer[] listValue = assertGetFromList(Integer[].class, list, "list");
        Assert.assertEquals(lst.size(), listValue.length);
        for (int ctr = 0; ctr < lst.size(); ctr++) {
            Assert.assertEquals(lst.get(ctr), listValue[ctr]);
        }
        Assert.assertEquals(l, assertGetFromList(Long.class, list, "long").longValue());
        Assert.assertEquals(type, ModelType.valueOf(assertGetFromList(String.class, list, "type")));
    }

    @Test
    public void testReadWriteAttributeListExpressionsStandalone() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new TestExtension(true)));

        ObjectName name = createObjectName(EXPR_DOMAIN + ":subsystem=test");
        String[] attrNames = new String[]{"roInt", "int", "bigint", "bigdec", "boolean", "bytes", "double", "string", "list", "long", "type"};
        AttributeList list = connection.getAttributes(name, attrNames);
        Assert.assertEquals(list.size(), attrNames.length);

        checkAttributeList(attrNames, list, "1", "2", "3", "4", "false", new byte[]{5, 6}, "7.0", "8",
                Collections.singletonList("9"), "10", "INT");

        list = new AttributeList();
        list.add(new Attribute("int", "${should.not.exist!!!!!:102}"));
        list.add(new Attribute("bigint", "${should.not.exist!!!!!:103}"));
        list.add(new Attribute("bigdec", "${should.not.exist!!!!!:104}"));
        list.add(new Attribute("boolean", "${should.not.exist!!!!!:true}"));
        list.add(new Attribute("bytes", new byte[]{105, 106}));
        list.add(new Attribute("double", "${should.not.exist!!!!!:107.0}"));
        list.add(new Attribute("string", "${should.not.exist!!!!!:108}"));
        list.add(new Attribute("list", new String[]{"${should.not.exist!!!!!:109}"}));
        list.add(new Attribute("long", "${should.not.exist!!!!!:110L}"));
        list.add(new Attribute("type", "${should.not.exist!!!!!:STRING}"));
        connection.setAttributes(name, list);

        list = connection.getAttributes(name, attrNames);
        checkAttributeList(attrNames, list, "1", "${should.not.exist!!!!!:102}", "${should.not.exist!!!!!:103}", "${should.not.exist!!!!!:104}", "${should.not.exist!!!!!:true}", new byte[]{105, 106}, "${should.not.exist!!!!!:107.0}", "${should.not.exist!!!!!:108}",
                Collections.singletonList("${should.not.exist!!!!!:109}"), "${should.not.exist!!!!!:110L}", "${should.not.exist!!!!!:STRING}");
    }

    private void checkAttributeList(String[] attrNames, AttributeList list, String roInt, String i, String bi, String bd, String b,
                                    byte[] bytes, String d, String s, List<String> lst, String l, String type) {
        Assert.assertEquals(list.size(), attrNames.length);

        Assert.assertEquals(roInt, assertGetFromList(String.class, list, "roInt"));
        Assert.assertEquals(i, assertGetFromList(String.class, list, "int"));
        Assert.assertEquals(bi, assertGetFromList(String.class, list, "bigint"));
        Assert.assertEquals(bd, assertGetFromList(String.class, list, "bigdec"));
        Assert.assertEquals(b, assertGetFromList(String.class, list, "boolean"));
        assertEqualByteArray(assertGetFromList(byte[].class, list, "bytes"), bytes);
        Assert.assertEquals(d, assertGetFromList(String.class, list, "double"));
        Assert.assertEquals(s, assertGetFromList(String.class, list, "string"));
        String[] listValue = assertGetFromList(String[].class, list, "list");
        Assert.assertEquals(lst.size(), listValue.length);
        for (int ctr = 0; ctr < lst.size(); ctr++) {
            Assert.assertEquals(lst.get(ctr), listValue[ctr]);
        }
        Assert.assertEquals(l, assertGetFromList(String.class, list, "long"));
        Assert.assertEquals(type, assertGetFromList(String.class, list, "type"));
    }

    @Test
    public void testInvokeOperationStandalone() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new TestExtension()));

        ObjectName name = createObjectName(LEGACY_DOMAIN + ":subsystem=test");

        ModelControllerResourceDefinition.VoidOperationNoParams.INSTANCE.invoked = false;
        Assert.assertNull(connection.invoke(name, ModelControllerResourceDefinition.VoidOperationNoParams.OPERATION_JMX_NAME, null, null));
        Assert.assertTrue(ModelControllerResourceDefinition.VoidOperationNoParams.INSTANCE.invoked);

        String result = assertCast(String.class, connection.invoke(
                name,
                ModelControllerResourceDefinition.IntOperationWithParams.OPERATION_JMX_NAME,
                new Object[]{100L, new String[]{"A"}, Collections.singletonMap("test", 3), 5, 5},
                new String[]{Long.class.getName(), String[].class.getName(), Map.class.getName(), Integer.class.getName(), Integer.class.getName()}));
        Assert.assertEquals("A105", result);
        Assert.assertTrue(ModelControllerResourceDefinition.IntOperationWithParams.INSTANCE_NO_EXPRESSIONS.invoked);

        MBeanInfo info = connection.getMBeanInfo(name);
        CompositeType complexType = assertCast(CompositeType.class, findAttribute(info.getAttributes(), "complex").getOpenType());
        CompositeData complexData = createComplexData(connection, complexType, 5, BigDecimal.valueOf(10.3d));
        Assert.assertEquals(complexData, assertCast(CompositeData.class, connection.invoke(
                name,
                ModelControllerResourceDefinition.ComplexOperation.OPERATION_NAME,
                new Object[]{complexData},
                new String[]{CompositeData.class.getName()})));
    }

    @Test
    public void testInvokeOperationDomain() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.DOMAIN_SERVER, new TestExtension()));

        ObjectName name = createObjectName(LEGACY_DOMAIN + ":subsystem=test");

        ModelControllerResourceDefinition.VoidOperationNoParams.INSTANCE.invoked = false;
        Assert.assertNull(connection.invoke(name, ModelControllerResourceDefinition.VoidOperationNoParams.OPERATION_JMX_NAME, new Object[0], new String[0]));
        Assert.assertTrue(ModelControllerResourceDefinition.VoidOperationNoParams.INSTANCE.invoked);

        connection.invoke(
                name,
                ModelControllerResourceDefinition.IntOperationWithParams.OPERATION_JMX_NAME,
                new Object[]{100L, new String[]{"A"}, Collections.singletonMap("test", 3), 5, 5},
                new String[]{Long.class.getName(), String[].class.getName(), Map.class.getName(), Integer.class.getName(), Integer.class.getName()});
    }

    @Test
    public void testInvokeOperationExpressionsStandalone() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new TestExtension(true)));

        ObjectName name = createObjectName(EXPR_DOMAIN + ":subsystem=test");

        ModelControllerResourceDefinition.VoidOperationNoParams.INSTANCE.invoked = false;
        Assert.assertNull(connection.invoke(name, ModelControllerResourceDefinition.VoidOperationNoParams.OPERATION_JMX_NAME, null, null));
        Assert.assertTrue(ModelControllerResourceDefinition.VoidOperationNoParams.INSTANCE.invoked);

        String result = assertCast(String.class, connection.invoke(
                name,
                ModelControllerResourceDefinition.IntOperationWithParams.OPERATION_JMX_NAME,
                new Object[]{"${should.not.exist!!!!!:100}", new String[]{"${should.not.exist!!!!!:A}"}, Collections.singletonMap("test", "${should.not.exist!!!!!:3}"), "${should.not.exist!!!!!:5}", "${should.not.exist!!!!!:5}"},
                new String[]{Long.class.getName(), String[].class.getName(), Map.class.getName(), Integer.class.getName(), Integer.class.getName()}));
        Assert.assertEquals("A105", result);
        Assert.assertTrue(ModelControllerResourceDefinition.IntOperationWithParams.INSTANCE_EXPRESSIONS.invoked);

        MBeanInfo info = connection.getMBeanInfo(name);
        CompositeType complexType = assertCast(CompositeType.class, findAttribute(info.getAttributes(), "complex").getOpenType());
        CompositeData complexData = createComplexData(connection, complexType, "${should.not.exist!!!!!:5}", "${should.not.exist!!!!!:10}");
        Assert.assertEquals(complexData, assertCast(CompositeData.class, connection.invoke(
                name,
                ModelControllerResourceDefinition.ComplexOperation.OPERATION_NAME,
                new Object[]{complexData},
                new String[]{CompositeData.class.getName()})));
    }


    @Test
    public void testAddMethodSingleFixedChild() throws Exception {
        final ObjectName testObjectName = createObjectName(LEGACY_DOMAIN + ":subsystem=test");
        final ObjectName childObjectName = createObjectName(LEGACY_DOMAIN + ":subsystem=test,single=only");
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new SubystemWithSingleFixedChildExtension()));

        Set<ObjectName> names = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":subsystem=test,*"), null);
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.contains(testObjectName));

        MBeanInfo subsystemInfo = connection.getMBeanInfo(testObjectName);
        Assert.assertEquals(0, subsystemInfo.getAttributes().length);
        Assert.assertEquals(1, subsystemInfo.getOperations().length);
        OpenMBeanOperationInfo op = findOperation(subsystemInfo.getOperations(), "addSingleOnly");
        Assert.assertEquals("add", op.getDescription());
        Assert.assertEquals(1, op.getSignature().length);
        Assert.assertEquals(Integer.class.getName(), op.getSignature()[0].getType());

        connection.invoke(testObjectName, "addSingleOnly", new Object[]{123}, new String[]{String.class.getName()});

        names = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":subsystem=test,*"), null);
        Assert.assertEquals(2, names.size());
        Assert.assertTrue(names.contains(testObjectName));
        Assert.assertTrue(names.contains(childObjectName));

        subsystemInfo = connection.getMBeanInfo(testObjectName);
        Assert.assertEquals(0, subsystemInfo.getAttributes().length);
        Assert.assertEquals(1, subsystemInfo.getOperations().length);
        op = findOperation(subsystemInfo.getOperations(), "addSingleOnly");
        Assert.assertEquals("add", op.getDescription());
        Assert.assertEquals(1, op.getSignature().length);
        Assert.assertEquals(Integer.class.getName(), op.getSignature()[0].getType());

        MBeanInfo childInfo = connection.getMBeanInfo(childObjectName);
        Assert.assertEquals(1, childInfo.getAttributes().length);
        Assert.assertEquals(Integer.class.getName(), childInfo.getAttributes()[0].getType());
        Assert.assertEquals(1, childInfo.getOperations().length);
        op = findOperation(childInfo.getOperations(), REMOVE);
        Assert.assertEquals("remove", op.getDescription());
        Assert.assertEquals(0, op.getSignature().length);

        Assert.assertEquals(123, connection.getAttribute(childObjectName, "attr"));

        try {
            connection.invoke(testObjectName, "addSingleOnly", new Object[]{123}, new String[]{String.class.getName()});
            Assert.fail("Should not have been able to register a duplicate resource");
        } catch (Exception expected) {
            //expected
        }

        connection.invoke(childObjectName, REMOVE, new Object[]{}, new String[]{});

        names = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":subsystem=test,*"), null);
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.contains(testObjectName));
    }

    @Test
    public void testAddMethodSiblingChildren() throws Exception {
        final ObjectName testObjectName = createObjectName(LEGACY_DOMAIN + ":subsystem=test");
        final ObjectName child1ObjectName = createObjectName(LEGACY_DOMAIN + ":subsystem=test,siblings=test1");
        final ObjectName child2ObjectName = createObjectName(LEGACY_DOMAIN + ":subsystem=test,siblings=test2");
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new SubystemWithSiblingChildrenChildExtension()));

        Set<ObjectName> names = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":subsystem=test,*"), null);
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.contains(testObjectName));

        MBeanInfo subsystemInfo = connection.getMBeanInfo(testObjectName);
        Assert.assertEquals(0, subsystemInfo.getAttributes().length);
        Assert.assertEquals(1, subsystemInfo.getOperations().length);
        OpenMBeanOperationInfo op = findOperation(subsystemInfo.getOperations(), "addSiblings");
        Assert.assertEquals("add", op.getDescription());
        Assert.assertEquals(2, op.getSignature().length);
        Assert.assertEquals(String.class.getName(), op.getSignature()[0].getType());
        Assert.assertEquals(Integer.class.getName(), op.getSignature()[1].getType());

        connection.invoke(testObjectName, "addSiblings", new Object[]{"test1", 123}, new String[]{String.class.getName(), String.class.getName()});

        names = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":subsystem=test,*"), null);
        Assert.assertEquals(2, names.size());
        Assert.assertTrue(names.contains(testObjectName));
        Assert.assertTrue(names.contains(child1ObjectName));

        subsystemInfo = connection.getMBeanInfo(testObjectName);
        Assert.assertEquals(0, subsystemInfo.getAttributes().length);
        Assert.assertEquals(1, subsystemInfo.getOperations().length);
        op = findOperation(subsystemInfo.getOperations(), "addSiblings");
        Assert.assertEquals("add", op.getDescription());
        Assert.assertEquals(2, op.getSignature().length);
        Assert.assertEquals(String.class.getName(), op.getSignature()[0].getType());
        Assert.assertEquals(Integer.class.getName(), op.getSignature()[1].getType());

        MBeanInfo childInfo = connection.getMBeanInfo(child1ObjectName);
        Assert.assertEquals(1, childInfo.getAttributes().length);
        Assert.assertEquals(Integer.class.getName(), childInfo.getAttributes()[0].getType());
        Assert.assertEquals(1, childInfo.getOperations().length);
        op = findOperation(childInfo.getOperations(), REMOVE);
        Assert.assertEquals("remove", op.getDescription());
        Assert.assertEquals(0, op.getSignature().length);

        connection.invoke(testObjectName, "addSiblings", new Object[]{"test2", 456}, new String[]{String.class.getName(), String.class.getName()});

        names = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":subsystem=test,*"), null);
        Assert.assertEquals(3, names.size());
        Assert.assertTrue(names.contains(testObjectName));
        Assert.assertTrue(names.contains(child1ObjectName));
        Assert.assertTrue(names.contains(child2ObjectName));

        subsystemInfo = connection.getMBeanInfo(testObjectName);
        Assert.assertEquals(0, subsystemInfo.getAttributes().length);
        Assert.assertEquals(1, subsystemInfo.getOperations().length);
        op = findOperation(subsystemInfo.getOperations(), "addSiblings");
        Assert.assertEquals("add", op.getDescription());
        Assert.assertEquals(2, op.getSignature().length);
        Assert.assertEquals(String.class.getName(), op.getSignature()[0].getType());
        Assert.assertEquals(Integer.class.getName(), op.getSignature()[1].getType());

        childInfo = connection.getMBeanInfo(child1ObjectName);
        Assert.assertEquals(1, childInfo.getAttributes().length);
        Assert.assertEquals(Integer.class.getName(), childInfo.getAttributes()[0].getType());
        Assert.assertEquals(1, childInfo.getOperations().length);
        op = findOperation(childInfo.getOperations(), REMOVE);
        Assert.assertEquals("remove", op.getDescription());
        Assert.assertEquals(0, op.getSignature().length);

        childInfo = connection.getMBeanInfo(child2ObjectName);
        Assert.assertEquals(1, childInfo.getAttributes().length);
        Assert.assertEquals(Integer.class.getName(), childInfo.getAttributes()[0].getType());
        Assert.assertEquals(1, childInfo.getOperations().length);
        op = findOperation(childInfo.getOperations(), REMOVE);
        Assert.assertEquals("remove", op.getDescription());
        Assert.assertEquals(0, op.getSignature().length);

        Assert.assertEquals(123, connection.getAttribute(child1ObjectName, "attr"));
        Assert.assertEquals(456, connection.getAttribute(child2ObjectName, "attr"));

        connection.invoke(child1ObjectName, REMOVE, new Object[]{}, new String[]{});

        names = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":subsystem=test,*"), null);
        Assert.assertEquals(2, names.size());
        Assert.assertTrue(names.contains(testObjectName));
        Assert.assertTrue(names.contains(child2ObjectName));

        connection.invoke(child2ObjectName, REMOVE, new Object[]{}, new String[]{});

        names = connection.queryNames(createObjectName(LEGACY_DOMAIN + ":subsystem=test,*"), null);
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.contains(testObjectName));
    }

    @Test
    public void testResolveExpressions() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new BaseAdditionalInitialization(ProcessType.STANDALONE_SERVER));
        System.clearProperty("jboss.test.resolve.expressions.test");
        Assert.assertEquals("123", connection.invoke(LEGACY_ROOT_NAME, "resolveExpression", new String[]{"${jboss.test.resolve.expressions.test:123}"}, new String[]{String.class.getName()}));

        try {
            connection.invoke(LEGACY_ROOT_NAME, "resolveExpression", new String[]{"${jboss.test.resolve.expressions.test}"}, new String[]{String.class.getName()});
            Assert.fail("Should not have been able to resolve non-existent property");
        } catch (Exception expected) {
            //expected
        }

    }

    @Test
    public void testAttributeChangeNotificationUsingMSc() throws Exception {
        KernelServices kernelServices = setup(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new TestExtension()));
        ServiceController<?> service = kernelServices.getContainer().getService(MBeanServerService.SERVICE_NAME);
        MBeanServer mbeanServer = MBeanServer.class.cast(service.awaitValue(5, TimeUnit.MINUTES));

        doTestAttributeChangeNotification(mbeanServer, true);
    }

    @Test
    public void testAttributeChangeNotificationUsingManagementFactory() throws Exception {
        setup(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new TestExtension()));
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

        doTestAttributeChangeNotification(mbeanServer, true);
    }

    @Test
    public void testAttributeChangeNotificationUsingRemoteJMXConnector() throws Exception {
        setup(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new TestExtension()));

        JMXConnectorServer connectorServer = startLocalConnectorServer(ManagementFactory.getPlatformMBeanServer());
        JMXConnector connector = JMXConnectorFactory.connect(connectorServer.getAddress());

        try {
            doTestAttributeChangeNotification(connector.getMBeanServerConnection(), true);
        } finally {
            connector.close();
            connectorServer.stop();
        }
    }

    @Test
    public void testAttributeChangeNotificationUsingRemotingConnector() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new TestExtension()));

        doTestAttributeChangeNotification(connection, false);
    }

    private void doTestAttributeChangeNotification(MBeanServerConnection connection, boolean notificationListenerOperationsMustSucceed) throws Exception {
        ObjectName name = createObjectName(LEGACY_DOMAIN + ":subsystem=test");

        final CountDownLatch notificationEmitted = new CountDownLatch(1);
        final AtomicReference<Notification> notification = new AtomicReference<>();
        NotificationListener listener = new NotificationListener() {
            @Override
            public void handleNotification(javax.management.Notification notif, Object handback) {
                notification.set(notif);
                notificationEmitted.countDown();

            }
        };
        NotificationFilterSupport filter = new NotificationFilterSupport();
        filter.enableType(AttributeChangeNotification.ATTRIBUTE_CHANGE);

        try {
            connection.addNotificationListener(name, listener, filter, null);
            if (!notificationListenerOperationsMustSucceed) {
                Assert.fail("Adding the notification listener must fail");
            }
        } catch (IOException | RuntimeOperationsException e) {
            if (notificationListenerOperationsMustSucceed) {
                Assert.fail("Unexpected exception when adding the notification listener");
            } else {
                Assert.assertTrue(e.getCause() instanceof UnsupportedOperationException || e.getCause().getCause() instanceof UnsupportedOperationException);
            }
        }

        connection.setAttribute(name, new Attribute("int", 102));

        if (notificationListenerOperationsMustSucceed) {
            Assert.assertTrue("Did not receive expected notification", notificationEmitted.await(1, TimeUnit.SECONDS));
            Notification notif = notification.get();
            Assert.assertTrue(notif instanceof AttributeChangeNotification);
            AttributeChangeNotification attributeChangeNotification = (AttributeChangeNotification) notif;
            Assert.assertEquals(AttributeChangeNotification.ATTRIBUTE_CHANGE, attributeChangeNotification.getType());
            Assert.assertEquals(name, attributeChangeNotification.getSource());
            Assert.assertEquals(Integer.class.getName(), attributeChangeNotification.getAttributeType());
            Assert.assertEquals("int", attributeChangeNotification.getAttributeName());
            Assert.assertEquals(2, attributeChangeNotification.getOldValue());
            Assert.assertEquals(102, attributeChangeNotification.getNewValue());
        } else {
            Assert.assertFalse("Did receive unexpected notification", notificationEmitted.await(500, TimeUnit.MILLISECONDS));
        }

        connection.removeNotificationListener(name, listener, filter, null);
    }

    @Test
    public void testMBeanServerNotification_REGISTRATION_NOTIFICATIONUsingMsc() throws Exception {
        KernelServices kernelServices = setup(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new SubystemWithSingleFixedChildExtension()));
        ServiceController<?> service = kernelServices.getContainer().getService(MBeanServerService.SERVICE_NAME);
        MBeanServer mbeanServer = MBeanServer.class.cast(service.getValue());

        doTestMBeanServerNotification_REGISTRATION_NOTIFICATION(mbeanServer, true);
    }

    @Test
    public void testMBeanServerNotification_REGISTRATION_NOTIFICATIONUsingManagementFactory() throws Exception {
        setup(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new SubystemWithSingleFixedChildExtension()));
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

        doTestMBeanServerNotification_REGISTRATION_NOTIFICATION(mbeanServer, true);
    }

    @Test
    public void testMBeanServerNotification_REGISTRATION_NOTIFICATIONUsingRemotingConnector() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new SubystemWithSingleFixedChildExtension()));

        doTestMBeanServerNotification_REGISTRATION_NOTIFICATION(connection, false);
    }

    @Test
    public void testMBeanServerNotification_REGISTRATION_NOTIFICATIONUsingRemoteJMXConnector() throws Exception {
        setup(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new SubystemWithSingleFixedChildExtension()));

        JMXConnectorServer connectorServer = startLocalConnectorServer(ManagementFactory.getPlatformMBeanServer());
        JMXConnector connector = JMXConnectorFactory.connect(connectorServer.getAddress());

        try {
            doTestMBeanServerNotification_REGISTRATION_NOTIFICATION(connector.getMBeanServerConnection(), true);
        } finally {
            connector.close();
            connectorServer.stop();
        }
    }

    public void doTestMBeanServerNotification_REGISTRATION_NOTIFICATION(MBeanServerConnection connection, boolean mustReceiveNotification) throws Exception {
        final ObjectName testObjectName = createObjectName(LEGACY_DOMAIN + ":subsystem=test");
        final ObjectName childObjectName = createObjectName(LEGACY_DOMAIN + ":subsystem=test,single=only");

        final CountDownLatch notificationEmitted = new CountDownLatch(1);
        final AtomicReference<Notification> notification = new AtomicReference<>();
        NotificationListener listener = new MbeanServerNotificationListener(notification, notificationEmitted, LEGACY_DOMAIN);
        NotificationFilterSupport filter = new NotificationFilterSupport();
        filter.enableType(MBeanServerNotification.REGISTRATION_NOTIFICATION);

        connection.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, filter, null);

        // add a management resource
        connection.invoke(testObjectName, "addSingleOnly", new Object[]{123}, new String[]{String.class.getName()});

        if (mustReceiveNotification) {
            Assert.assertTrue("Did not receive expected notification", notificationEmitted.await(1, TimeUnit.SECONDS));
            Notification notif = notification.get();
            Assert.assertNotNull(notif);
            Assert.assertTrue(notif instanceof MBeanServerNotification);
            MBeanServerNotification mBeanServerNotification = (MBeanServerNotification) notif;
            Assert.assertEquals(MBeanServerNotification.REGISTRATION_NOTIFICATION, notif.getType());
            Assert.assertEquals(childObjectName, mBeanServerNotification.getMBeanName());
        } else {
            Assert.assertFalse("Did receive unexpected notification", notificationEmitted.await(500, TimeUnit.MILLISECONDS));
        }

        connection.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, filter, null);
    }

    @Test
    public void testMBeanServerNotification_UNREGISTRATION_NOTIFICATIONUsingMsc() throws Exception {
        KernelServices kernelServices = setup(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new SubystemWithSingleFixedChildExtension()));
        ServiceController<?> service = kernelServices.getContainer().getService(MBeanServerService.SERVICE_NAME);
        MBeanServer mBeanServer = MBeanServer.class.cast(service.awaitValue(5, TimeUnit.MINUTES));

        doTestMBeanServerNotification_UNREGISTRATION_NOTIFICATION(mBeanServer, true);
    }

    @Test
    public void testMBeanServerNotification_UNREGISTRATION_NOTIFICATIONUsingManagementFactory() throws Exception {
        setup(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new SubystemWithSingleFixedChildExtension()));
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

        doTestMBeanServerNotification_UNREGISTRATION_NOTIFICATION(mbeanServer, true);
    }

    @Test
    public void testMBeanServerNotification_UNREGISTRATION_NOTIFICATIONUsingRemotingConnector() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new SubystemWithSingleFixedChildExtension()));

        doTestMBeanServerNotification_UNREGISTRATION_NOTIFICATION(connection, false);
    }

    @Test
    public void testMBeanServerNotification_UNREGISTRATION_NOTIFICATIONUsingRemoteJMXConnector() throws Exception {
        setup(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new SubystemWithSingleFixedChildExtension()));

        JMXConnectorServer connectorServer = startLocalConnectorServer(ManagementFactory.getPlatformMBeanServer());
        JMXConnector connector = JMXConnectorFactory.connect(connectorServer.getAddress());

        try {
            doTestMBeanServerNotification_UNREGISTRATION_NOTIFICATION(connector.getMBeanServerConnection(), true);
        } finally {
            connector.close();
            connectorServer.stop();
        }
    }

    private void doTestMBeanServerNotification_UNREGISTRATION_NOTIFICATION(MBeanServerConnection connection, boolean mustReceiveNotification) throws Exception {
        final ObjectName testObjectName = createObjectName(LEGACY_DOMAIN + ":subsystem=test");
        final ObjectName childObjectName = createObjectName(LEGACY_DOMAIN + ":subsystem=test,single=only");

        final CountDownLatch notificationEmitted = new CountDownLatch(1);
        final AtomicReference<Notification> notification = new AtomicReference<>();

        NotificationListener listener = new MbeanServerNotificationListener(notification, notificationEmitted, LEGACY_DOMAIN);
        NotificationFilterSupport filter = new NotificationFilterSupport();
        filter.enableType(MBeanServerNotification.UNREGISTRATION_NOTIFICATION);

        connection.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, filter, null);

        // add a management resource
        connection.invoke(testObjectName, "addSingleOnly", new Object[]{123}, new String[]{String.class.getName()});
        // and remove it
        connection.invoke(childObjectName, "remove", new Object[0], new String[0]);

        if (mustReceiveNotification) {
            Assert.assertTrue("Did not receive expected notification", notificationEmitted.await(1, TimeUnit.SECONDS));
            Notification notif = notification.get();
            Assert.assertNotNull(notif);
            Assert.assertTrue(notif instanceof MBeanServerNotification);
            MBeanServerNotification mBeanServerNotification = (MBeanServerNotification) notif;
            Assert.assertEquals(MBeanServerNotification.UNREGISTRATION_NOTIFICATION, mBeanServerNotification.getType());
            Assert.assertEquals(childObjectName, mBeanServerNotification.getMBeanName());
        } else {
            Assert.assertFalse("Did receive unexpected notification", notificationEmitted.await(500, TimeUnit.MILLISECONDS));
        }

        connection.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, filter, null);
    }

    @Test
    public void testNestedComplexRuntimeTypeDefinitions() throws Exception {
        MBeanServerConnection connection = setupAndGetConnection(new MBeanInfoAdditionalInitialization(ProcessType.STANDALONE_SERVER, new ComplexRuntimeAttributesExtension()));
        ObjectName name = new ObjectName("jboss.as.expr:subsystem=test");

        TabularData mapOfMaps = (TabularData) connection.getAttribute(name, "map-of-maps");
        System.out.println(mapOfMaps);
        Assert.assertEquals(3, mapOfMaps.size());
        checkMapOfMapsEntry(1001, "Hello a", mapOfMaps, "A");
        checkMapOfMapsEntry(1002, "Hello b", mapOfMaps, "B");
        checkMapOfMapsEntry(1003, "Hello c", mapOfMaps, "C");
    }

    private void checkMapOfMapsEntry(long expectedOne, String expectedTwo, TabularData tabularData, String key) {
        CompositeData mapEntry = assertCast(CompositeData.class, tabularData.get(new Object[]{key}));
        CompositeData valueEntry = assertCast(CompositeData.class, mapEntry.get("value"));
        Assert.assertEquals(String.valueOf(expectedOne), valueEntry.get("one"));
        Assert.assertEquals(expectedTwo, valueEntry.get("two"));
    }



    private OpenMBeanOperationInfo findOperation(MBeanOperationInfo[] ops, String name) {
        for (MBeanOperationInfo op : ops) {
            Assert.assertNotNull(op.getName());
            if (op.getName().equals(name)) {
                return assertCast(OpenMBeanOperationInfo.class, op);
            }
        }
        Assert.fail("No op called " + name);
        return null;
    }

    private OpenMBeanAttributeInfo findAttribute(MBeanAttributeInfo[] attrs, String name) {
        for (MBeanAttributeInfo attr : attrs) {
            Assert.assertNotNull(attr.getName());
            if (attr.getName().equals(name)) {
                return assertCast(OpenMBeanAttributeInfo.class, attr);
            }
        }
        Assert.fail("No attr called " + name);
        return null;
    }

    private void assertEqualByteArray(byte[] bytes, byte... expected) {
        Assert.assertEquals(expected.length, bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            Assert.assertEquals(expected[i], bytes[i]);
        }
    }

    private CompositeData createComplexData(MBeanServerConnection connection, CompositeType type, int intValue, BigDecimal bigDecimalValue) throws Exception {
        Map<String, Object> items = new HashMap<String, Object>();
        items.put("int-value", intValue);
        //items.put("bigint-value", bigIntegerValue);
        items.put("bigdecimal-value", bigDecimalValue);
        CompositeDataSupport data = new CompositeDataSupport(type, items);
        return data;
    }

    private CompositeData createComplexData(MBeanServerConnection connection, CompositeType type, String intValue, String bigDecimalValue) throws Exception {
        Map<String, Object> items = new HashMap<String, Object>();
        items.put("int-value", intValue);
        //items.put("bigint-value", bigIntegerValue);
        items.put("bigdecimal-value", bigDecimalValue);
        CompositeDataSupport data = new CompositeDataSupport(type, items);
        return data;
    }

    private void assertMapType(OpenType<?> mapType, OpenType<?> keyType, OpenType<?> valueType) {
        TabularType type = assertCast(TabularType.class, mapType);
        Assert.assertEquals(1, type.getIndexNames().size());
        Assert.assertEquals("key", type.getIndexNames().get(0));
        Assert.assertEquals(2, type.getRowType().keySet().size());
        Assert.assertTrue(type.getRowType().keySet().contains("key"));
        Assert.assertTrue(type.getRowType().keySet().contains("value"));
        Assert.assertEquals(keyType, type.getRowType().getType("key"));
        Assert.assertEquals(valueType, type.getRowType().getType("value"));

    }

    private void checkComplexTypeInfo(CompositeType composite, boolean expressions, String prefix) {
        Set<String> keys = composite.keySet();
        Assert.assertEquals(2, keys.size());
        assertCompositeType(composite, "int-value", expressions ? String.class.getName() : Integer.class.getName(), (prefix != null ? prefix : "") + "int-value");
        assertCompositeType(composite, "bigdecimal-value", expressions ? String.class.getName() : BigDecimal.class.getName(), (prefix != null ? prefix : "") + "bigdecimal-value");
    }


    private void assertAttributeDescription(MBeanAttributeInfo attribute, String name, String type, String description, boolean readable,
                                            boolean writable) {
        Assert.assertEquals(name, attribute.getName());
        Assert.assertEquals(description, attribute.getDescription());
        Assert.assertEquals(type, attribute.getType());
        Assert.assertEquals(readable, attribute.isReadable());
        Assert.assertEquals(writable, attribute.isWritable());
    }

    private OpenType<?> assertCompositeType(CompositeType composite, String name, String type, String description) {
        return assertCompositeType(composite, name, type, description, true);
    }

    private OpenType<?> assertCompositeType(CompositeType composite, String name, String type, String description, boolean validateType) {
        Assert.assertTrue(composite.keySet().contains(name));
        if (validateType) {
            Assert.assertEquals(type, composite.getType(name).getTypeName());
        }
        Assert.assertEquals(description, composite.getDescription(name));
        return composite.getType(name);
    }

    private static <T> T assertGetFromList(Class<T> clazz, AttributeList list, String name) {
        Object value = null;
        List<javax.management.Attribute> attrs = list.asList();
        for (Attribute attr : attrs) {
            if (attr.getName().equals(name)) {
                value = attr.getValue();
                break;
            }
        }
        Assert.assertNotNull(value);
        return assertCast(clazz, value);
    }

    private static <T> T assertCast(Class<T> clazz, Object value) {
        Assert.assertTrue("value " + value.getClass().getName() + " can not be changed to a " + clazz.getName(), clazz.isAssignableFrom(value.getClass()));
        return clazz.cast(value);
    }


    private void checkSameMBeans(Set<ObjectInstance> instances, Set<ObjectName> objectNames) {
        for (ObjectInstance instance : instances) {
            Assert.assertTrue("Does not contain " + instance.getObjectName(), objectNames.contains(instance.getObjectName()));
        }
    }

    private void assertContainsNames(Set<ObjectName> objectNames, ObjectName... expected) {
        for (ObjectName name : expected) {
            Assert.assertTrue("Does not contain " + name, objectNames.contains(name));
        }
    }

    private static class MbeanServerNotificationListener implements NotificationListener {

        private final AtomicReference<Notification> notification;
        private final CountDownLatch latch;
        private final String domain;

        private MbeanServerNotificationListener(AtomicReference<Notification> notification, CountDownLatch latch, String domain) {
            this.notification = notification;
            this.latch = latch;
            this.domain = domain;
        }

        @Override
        public void handleNotification(Notification notification, Object handback) {
            if (notification instanceof MBeanServerNotification) {
                MBeanServerNotification notif = (MBeanServerNotification) notification;
                if (notif.getMBeanName().getDomain().equals(domain)) {
                    this.notification.set(notification);
                    this.latch.countDown();
                }
            }
        }
    }

    /*
     * Start a local RMI Server (similar to what the Attach API does when connecting locally using jconsole)
     */
    private static JMXConnectorServer startLocalConnectorServer(MBeanServer mBeanServer) throws IOException {
        JMXServiceURL serviceURL = new JMXServiceURL("service:jmx:rmi://localhost");
        JMXConnectorServer connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(serviceURL, new HashMap<String, String>(), mBeanServer);
        connectorServer.start();
        return connectorServer;
    }

    private KernelServices setup(BaseAdditionalInitialization additionalInitialization) throws Exception {
        // Parse the subsystem xml and install into the controller
        String subsystemXml = "<subsystem xmlns=\"" + Namespace.CURRENT.getUriString() + "\">"
                + "<expose-resolved-model domain-name=\"jboss.resolved\"/>"
                + "<expose-expression-model/>"
                + "<remoting-connector/>" + "</subsystem>"
                + additionalInitialization.getExtraXml();
        KernelServices kernelServices = createKernelServicesBuilder(additionalInitialization).setSubsystemXml(subsystemXml).build();
        return kernelServices;
    }

    private MBeanServerConnection getRemoteConnection() throws Exception {
        Assert.assertNull(jmxConnector);

        // Make sure that we can connect to the MBean server
        String host = "localhost";
        String urlString = System
                .getProperty("jmx.service.url", "service:jmx:remoting-jmx://" + host + ":" + JMX_PORT);
        JMXServiceURL serviceURL = new JMXServiceURL(urlString);

        // TODO this is horrible - for some reason after the first test the
        // second time we
        // start the JMX connector it takes time for it to appear
        long end = System.currentTimeMillis() + 10000;
        while (true) {
            try {
                JMXConnector jmxConnector = JMXConnectorFactory.connect(serviceURL, null);
                this.jmxConnector = jmxConnector;
                return jmxConnector.getMBeanServerConnection();
            } catch (Exception e) {
                if (System.currentTimeMillis() >= end) {
                    throw new RuntimeException(e);
                }
                Thread.sleep(50);
            }
        }
    }

    private MBeanServerConnection setupAndGetConnection(BaseAdditionalInitialization additionalInitialization) throws Exception {
        kernelServices = setup(additionalInitialization);
        return getRemoteConnection();
    }

    private static ObjectName createObjectName(String s) {
        try {
            return new ObjectName(s);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }


    private static class BaseAdditionalInitialization extends AdditionalInitialization {

        final ProcessType processType;

        public BaseAdditionalInitialization(ProcessType processType) {
            assert processType.isServer();
            this.processType = processType;
        }

        @Override
        protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry, Resource rootResource,
                                                        ManagementResourceRegistration rootRegistration,
                                                        RuntimeCapabilityRegistry capabilityRegistry) {
            rootRegistration.registerOperationHandler(ResolveExpressionHandler.DEFINITION, ResolveExpressionHandler.INSTANCE);
            AdditionalInitialization.registerCapabilities(capabilityRegistry, RemotingConnectorResource.REMOTING_CAPABILITY);
        }

        @Override
        protected void setupController(ControllerInitializer controllerInitializer) {
            controllerInitializer.addSocketBinding("server", JMX_PORT);
            controllerInitializer.addPath("jboss.controller.temp.dir", System.getProperty("java.io.tmpdir"), null);
        }

        @Override
        protected void addExtraServices(final ServiceTarget target) {
            ManagementRemotingServices.installRemotingManagementEndpoint(target, ManagementRemotingServices.MANAGEMENT_ENDPOINT, "localhost", EndpointService.EndpointType.MANAGEMENT);
            ServiceName tmpDirPath = ServiceName.JBOSS.append("server", "path", "jboss.controller.temp.dir");

            RemotingServices.installConnectorServicesForSocketBinding(target, ManagementRemotingServices.MANAGEMENT_ENDPOINT,
                    "server", SOCKET_BINDING_CAPABILITY.getCapabilityServiceName("server"), OptionMap.EMPTY,
                    null, null, ServiceName.parse("org.wildfly.management.socket-binding-manager"), Protocol.REMOTE.toString());
        }

        @Override
        protected ProcessType getProcessType() {
            return processType;
        }

        String getExtraXml() {
            return "";
        }
    }

    private static class MBeanInfoAdditionalInitialization extends BaseAdditionalInitialization {

        private final Extension extension;


        public MBeanInfoAdditionalInitialization(ProcessType processType, Extension extension) {
            super(processType);
            this.extension = extension;
        }

        @Override
        protected void addParsers(ExtensionRegistry extensionRegistry, XMLMapper xmlMapper) {
            extensionRegistry.initializeParsers(extension,"additional", xmlMapper);
        }

        @Override
        protected void initializeExtraSubystemsAndModel(ExtensionRegistry extensionRegistry, Resource rootResource,
                                                        ManagementResourceRegistration rootRegistration, RuntimeCapabilityRegistry capabilityRegistry) {
            super.initializeExtraSubystemsAndModel(extensionRegistry, rootResource, rootRegistration, capabilityRegistry);
            extension.initialize(extensionRegistry.getExtensionContext("additional", rootRegistration, ExtensionRegistryType.SLAVE));
        }

        @Override
        String getExtraXml() {
            return "<subsystem xmlns=\"" + TestExtension.NAMESPACE + "\"/>";
        }
    }

    static class SubystemWithSingleFixedChildExtension extends SubsystemWithChildrenExtension {
        @Override
        PathElement getChildElement() {
            return PathElement.pathElement("single", "only");
        }
    }

    static class SubystemWithSiblingChildrenChildExtension extends SubsystemWithChildrenExtension {
        @Override
        PathElement getChildElement() {
            return PathElement.pathElement("siblings");
        }
    }

}
