/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.util.Map.Entry;
import javax.management.JMException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.platform.mbean.ExtendedGarbageCollectorMBean.GcInfo;
import org.jboss.dmr.ModelNode;

/**
 * Utilities for working with platform mbeans.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class PlatformMBeanUtil {

    public static final int JVM_MAJOR_VERSION = Runtime.version().feature();

    public static String escapeMBeanName(final String toEscape) {
        return toEscape.replace(' ', '_');
    }

    public static String unescapeMBeanValue(final String toUnescape) {
        String unescaped = toUnescape.replace('_', ' ');
        return unescaped.equals(toUnescape) ? toUnescape : unescaped;
    }

    public static String getObjectNameStringWithNameKey(final String base, final String escapedValue) {
        final String value = unescapeMBeanValue(escapedValue);
        return base + ",name=" + value;
    }

    public static ObjectName getObjectNameWithNameKey(final String base, final String escapedValue) throws OperationFailedException {
        try {
            return new ObjectName(getObjectNameStringWithNameKey(base, escapedValue));
        } catch (MalformedObjectNameException e) {
            throw new OperationFailedException(e.toString());
        }
    }

    public static Object getMBeanAttribute(final ObjectName objectName, final String attribute) throws OperationFailedException {
        try {
            return ManagementFactory.getPlatformMBeanServer().getAttribute(objectName, attribute);
        } catch (ReflectionException e) {
            Throwable t = e.getTargetException();
            if (t instanceof SecurityException || t instanceof UnsupportedOperationException) {
                throw new OperationFailedException(e.toString());
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new RuntimeException(t);
            }
        } catch (JMException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Utility for converting {@link java.lang.management.MemoryUsage} to a detyped form.
     * @param memoryUsage the memory usage data object
     * @return the detyped representation
     */
    public static ModelNode getDetypedMemoryUsage(final MemoryUsage memoryUsage) {
        final ModelNode result = new ModelNode();
        if (memoryUsage != null) {
            result.get(PlatformMBeanConstants.INIT).set(memoryUsage.getInit());
            result.get(PlatformMBeanConstants.USED).set(memoryUsage.getUsed());
            result.get(PlatformMBeanConstants.COMMITTED).set(memoryUsage.getCommitted());
            result.get(PlatformMBeanConstants.MAX).set(memoryUsage.getMax());
        }
        return result;
    }

    /**
     * Utility for converting {@link java.lang.management.ThreadInfo} to a detyped form.
     *
     * @param threadInfo the thread information data object
     * @param includeBlockedTime whether the {@link PlatformMBeanConstants#BLOCKED_TIME} attribute is supported
     * @return the detyped representation
     */
    public static ModelNode getDetypedThreadInfo(final ThreadInfo threadInfo, boolean includeBlockedTime) {
        final ModelNode result = new ModelNode();

        result.get(PlatformMBeanConstants.THREAD_ID).set(threadInfo.getThreadId());
        result.get(PlatformMBeanConstants.THREAD_NAME).set(threadInfo.getThreadName());
        result.get(PlatformMBeanConstants.THREAD_STATE).set(threadInfo.getThreadState().name());
        if (includeBlockedTime) {
            result.get(PlatformMBeanConstants.BLOCKED_TIME).set(threadInfo.getBlockedTime());
        } else {
            result.get(PlatformMBeanConstants.BLOCKED_TIME);
        }
        result.get(PlatformMBeanConstants.BLOCKED_COUNT).set(threadInfo.getBlockedCount());
        result.get(PlatformMBeanConstants.WAITED_TIME).set(threadInfo.getWaitedTime());
        result.get(PlatformMBeanConstants.WAITED_COUNT).set(threadInfo.getWaitedCount());
        result.get(PlatformMBeanConstants.LOCK_INFO).set(getDetypedLockInfo(threadInfo.getLockInfo()));
        nullSafeSet(result.get(PlatformMBeanConstants.LOCK_NAME), threadInfo.getLockName());
        result.get(PlatformMBeanConstants.LOCK_OWNER_ID).set(threadInfo.getLockOwnerId());
        nullSafeSet(result.get(PlatformMBeanConstants.LOCK_OWNER_NAME), threadInfo.getLockOwnerName());
        final ModelNode stack = result.get(PlatformMBeanConstants.STACK_TRACE);
        stack.setEmptyList();
        for (StackTraceElement ste : threadInfo.getStackTrace()) {
            stack.add(getDetypedStackTraceElement(ste));
        }
        result.get(PlatformMBeanConstants.SUSPENDED).set(threadInfo.isSuspended());
        result.get(PlatformMBeanConstants.IN_NATIVE).set(threadInfo.isInNative());
        final ModelNode monitors = result.get(PlatformMBeanConstants.LOCKED_MONITORS);
        monitors.setEmptyList();
        for (MonitorInfo monitor : threadInfo.getLockedMonitors()) {
            monitors.add(getDetypedMonitorInfo(monitor));
        }
        final ModelNode synchronizers = result.get(PlatformMBeanConstants.LOCKED_SYNCHRONIZERS);
        synchronizers.setEmptyList();
        for (LockInfo lock : threadInfo.getLockedSynchronizers()) {
            synchronizers.add(getDetypedLockInfo(lock));
        }
        result.get(PlatformMBeanConstants.DAEMON).set(threadInfo.isDaemon());
        result.get(PlatformMBeanConstants.PRIORITY).set(threadInfo.getPriority());
        return result;
    }

    /**
     * Utility for converting {@link org.jboss.as.platform.mbean.ExtendedGarbageCollectorMBean.GcInfo} to a detyped form.
     *
     * @param gcInfo the gc information data object
     * @return the detyped representation
     */
    public static ModelNode getDetypedGcInfo(final GcInfo gcInfo) {
        final ModelNode result = new ModelNode();

        result.get(PlatformMBeanConstants.DURATION).set(gcInfo.getDuration());
        result.get(PlatformMBeanConstants.END_TIME).set(gcInfo.getEndTime());
        result.get(PlatformMBeanConstants.ID).set(gcInfo.getId());
        result.get(PlatformMBeanConstants.START_TIME).set(gcInfo.getStartTime());
        final ModelNode memUsageAfterGc = result.get(PlatformMBeanConstants.MEMORY_USAGE_AFTER_GC);
        for (Entry<String, MemoryUsage> entry : gcInfo.getMemoryUsageAfterGc().entrySet()) {
            memUsageAfterGc.add(entry.getKey(), getDetypedMemoryUsage(entry.getValue()));
        }
        final ModelNode memUsageBeforeGc = result.get(PlatformMBeanConstants.MEMORY_USAGE_BEFORE_GC);
        for (Entry<String, MemoryUsage> entry : gcInfo.getMemoryUsageBeforeGc().entrySet()) {
            memUsageBeforeGc.add(entry.getKey(), getDetypedMemoryUsage(entry.getValue()));
        }
        return result;
    }

    private static void nullSafeSet(final ModelNode node, final String value) {
        if (value != null) {
            node.set(value);
        }
    }

    private static ModelNode getDetypedLockInfo(final LockInfo lockInfo) {
        final ModelNode result = new ModelNode();
        if (lockInfo != null) {
            result.get(PlatformMBeanConstants.CLASS_NAME).set(lockInfo.getClassName());
            result.get(PlatformMBeanConstants.IDENTITY_HASH_CODE).set(lockInfo.getIdentityHashCode());
        }
        return result;
    }

    private static ModelNode getDetypedMonitorInfo(final MonitorInfo monitorInfo) {
        final ModelNode result = getDetypedLockInfo(monitorInfo);
        if (monitorInfo != null) {
            result.get(PlatformMBeanConstants.LOCKED_STACK_DEPTH).set(monitorInfo.getLockedStackDepth());
            final ModelNode frame = getDetypedStackTraceElement(monitorInfo.getLockedStackFrame());
            result.get(PlatformMBeanConstants.LOCKED_STACK_FRAME).set(frame);
        }
        return result;
    }

    private static ModelNode getDetypedStackTraceElement(final StackTraceElement stackTraceElement) {
        final ModelNode result = new ModelNode();
        if (stackTraceElement != null) {
            nullSafeSet(result.get(PlatformMBeanConstants.FILE_NAME), stackTraceElement.getFileName());
            result.get(PlatformMBeanConstants.LINE_NUMBER).set(stackTraceElement.getLineNumber());
            ModelNode cl = result.get(PlatformMBeanConstants.CLASS_LOADER_NAME);
            if (stackTraceElement.getClassLoaderName() != null) {
                cl.set(stackTraceElement.getClassLoaderName());
            }
            result.get(PlatformMBeanConstants.CLASS_NAME).set(stackTraceElement.getClassName());
            result.get(PlatformMBeanConstants.METHOD_NAME).set(stackTraceElement.getMethodName());
            ModelNode mn = result.get(PlatformMBeanConstants.MODULE_NAME);
            if (stackTraceElement.getModuleName() != null) {
                mn.set(stackTraceElement.getModuleName());
            }
            ModelNode mv = result.get(PlatformMBeanConstants.MODULE_VERSION);
            if (stackTraceElement.getModuleVersion()!= null) {
                mv.set(stackTraceElement.getModuleVersion());
            }
            result.get(PlatformMBeanConstants.NATIVE_METHOD).set(stackTraceElement.isNativeMethod());
        }
        return result;
    }

    private PlatformMBeanUtil() {

    }
    static final String RESOURCE_NAME = PlatformMBeanUtil.class.getPackage().getName() + ".LocalDescriptions";

    static StandardResourceDescriptionResolver getResolver(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder("");
        for (String kp : keyPrefix) {
            if (prefix.length() > 0) {
                prefix.append('.');
            }
            prefix.append(kp);
        }
        return new StandardResourceDescriptionResolver(prefix.toString(), RESOURCE_NAME, CommonAttributes.class.getClassLoader(), true, false);
    }
}
