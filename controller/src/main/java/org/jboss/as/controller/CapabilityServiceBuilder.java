/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller;

import java.util.function.Consumer;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.Service;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * A builder for an individual service in a {@code CapabilityServiceTarget}.
 * Create an instance via the {@link CapabilityServiceTarget#addService()} method.
 *
 * @param <T> the service type
 * @author Tomaz Cerar (c) 2017 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface CapabilityServiceBuilder<T> extends RequirementServiceBuilder<T> {

    @Override
    CapabilityServiceBuilder<T> setInitialMode(ServiceController.Mode mode);

    @Override
    CapabilityServiceBuilder<T> setInstance(Service service);

    @Override
    CapabilityServiceBuilder<T> addListener(LifecycleListener listener);

    /**
     * Provide value under given capability.
     *
     * @param capability capability provided value represents
     * @param <V> consumed value type
     * @return consumer providing value
     */
    <V> Consumer<V> provides(final RuntimeCapability<?> capability);

    /**
     * Provide value under given capabilities.
     *
     * @param capabilities capabilities provided value represent
     * @param <V> consumed value type
     * @return consumer providing value
     */
    <V> Consumer<V> provides(final RuntimeCapability<?>... capabilities);

    /**
     * Provide value under given capability and aliases.
     *
     * @param capability name of capability provided value represent
     * @param alias alias name of provided value
     * @param aliases other aliases of provided value
     * @param <V> consumed value type
     * @return consumer providing value
     */
    <V> Consumer<V> provides(final RuntimeCapability<?> capability, final ServiceName alias, final ServiceName... aliases);

    /**
     * Provide value under given capabilities and alias names.
     *
     * @param capabilities capabilities provided value represent
     * @param aliases alias names of provided value
     * @param <V> consumed value type
     * @return consumer providing value
     */
    <V> Consumer<V> provides(final RuntimeCapability<?>[] capabilities, final ServiceName[] aliases);
}
