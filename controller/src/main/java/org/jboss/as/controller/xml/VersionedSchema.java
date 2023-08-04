/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.controller.xml;

import org.jboss.staxmapper.Versioned;

/**
 * A versioned schema, whose namespace is a versioned namespace.
 * @author Paul Ferraro
 */
public interface VersionedSchema<V extends Comparable<V>, S extends VersionedSchema<V, S>> extends Versioned<V, S>, Schema {

    /**
     * Returns the versioned namespace of this attribute/element.
     * @return the versioned namespace of this attribute/element.
     */
    @Override
    VersionedNamespace<V, S> getNamespace();

    @Override
    default V getVersion() {
        return this.getNamespace().getVersion();
    }
}
