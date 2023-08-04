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

import java.util.Set;

import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLMapper;

/**
 * A versioned schema for an XML element.
 * @author Paul Ferraro
 * @param S the schema type
 * @param T the type upon which this XML reader operates
 */
public interface XMLElementSchema<S extends XMLElementSchema<S, T>, T> extends IntVersionSchema<S>, XMLElementReader<T> {

    /**
     * Creates a StAX mapper from a set of schemas.
     * @param <T> the xml reader context type
     * @param <S> the schema type
     * @param schemas a set of XML element schemas
     * @return a StAX mapper
     */
    static <T, S extends XMLElementSchema<S, T>> XMLMapper createXMLMapper(Set<S> schemas) {
        XMLMapper mapper = XMLMapper.Factory.create();
        for (S schema : schemas) {
            mapper.registerRootElement(schema.getQualifiedName(), schema);
        }
        return mapper;
    }
}
