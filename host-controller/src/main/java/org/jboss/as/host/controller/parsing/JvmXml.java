/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequiredElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.host.controller.model.jvm.JvmAttributes;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Utilities for parsing and marshalling domain.xml and host.xml JVM configurations.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Kabir Khan
 */
public class JvmXml {

    public static void parseJvm(final XMLExtendedStreamReader reader, final ModelNode parentAddress, final IntVersion version,
            final String expectedNs, final List<ModelNode> updates, final Set<String> jvmNames, final boolean server) throws XMLStreamException {
        switch (version.major()) {
            case 1:
            case 2:
                parseJvm_1_0(reader, parentAddress, expectedNs, updates, jvmNames, server);
                break;
            default:
                parseJvm_3_0(reader, parentAddress, expectedNs, updates, jvmNames, server);
                break;
        }
    }

    private static String parseJvmAttributes(XMLExtendedStreamReader reader, ModelNode addOp, Set<String> jvmNames, boolean server) throws XMLStreamException {
        // Handle attributes
        String name = null;
        boolean debugEnabled = false;
        String debugOptions = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME: {
                        if (name != null)
                            throw ParseUtils.duplicateAttribute(reader, attribute.getLocalName());

                        if (!jvmNames.add(value)) {
                            throw ControllerLogger.ROOT_LOGGER.duplicateDeclaration("JVM", value, reader.getLocation());
                        }
                        name = value;
                        break;
                    }
                    case JAVA_HOME: {
                        JvmAttributes.JAVA_HOME.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case TYPE: {
                        try {
                            // Validate the type against the enum
                            JvmAttributes.TYPE.parseAndSetParameter(value, addOp, reader);
                        } catch (final IllegalArgumentException e) {
                            throw ParseUtils.invalidAttributeValue(reader, i);
                        }
                        break;
                    }
                    case DEBUG_ENABLED: {
                        if (!server) {
                            throw ParseUtils.unexpectedAttribute(reader, i);
                        }
                        debugEnabled = Boolean.parseBoolean(value);
                        JvmAttributes.DEBUG_ENABLED.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case DEBUG_OPTIONS: {
                        if (!server) {
                            throw ParseUtils.unexpectedAttribute(reader, i);
                        }
                        debugOptions = value;
                        JvmAttributes.DEBUG_OPTIONS.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case ENV_CLASSPATH_IGNORED: {
                        JvmAttributes.ENV_CLASSPATH_IGNORED.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default:
                        throw ParseUtils.unexpectedAttribute(reader, i);
                }
            }
        }
        if (name == null) {
            // FIXME and fix xsd. A name is only required at domain and host
            // level (i.e. when wrapped in <jvms/>). At server-group and server
            // levels it can be unnamed, in which case configuration from
            // domain and host levels aren't mixed in. OR make name required in xsd always
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.NAME));
        }
        if (debugEnabled && debugOptions == null) {
            throw ParseUtils.missingRequired(reader, EnumSet.of(Attribute.DEBUG_OPTIONS));
        }

        return name;
    }
    private static void parseJvm_1_0(final XMLExtendedStreamReader reader, final ModelNode parentAddress, final String expectedNs, final List<ModelNode> updates,
            final Set<String> jvmNames, final boolean server) throws XMLStreamException {

        ModelNode addOp = Util.createAddOperation();

        String name = parseJvmAttributes(reader, addOp, jvmNames, server);

        final ModelNode address = parentAddress.clone();
        address.add(ModelDescriptionConstants.JVM, name);
        addOp.get(OP_ADDR).set(address);
        updates.add(addOp);

        // Handle elements
        boolean hasJvmOptions = false;
        boolean hasEnvironmentVariables = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case HEAP: {
                    parseHeap(reader, addOp);
                    break;
                }
                case PERMGEN: {
                    parsePermgen(reader, addOp);
                    break;
                }
                case STACK: {
                    parseStack(reader, addOp);
                    break;
                }
                case AGENT_LIB: {
                    parseAgentLib(reader, addOp);
                    break;
                }
                case AGENT_PATH: {
                    parseAgentPath(reader, addOp);
                    break;
                }
                case JAVA_AGENT: {
                    parseJavaagent(reader, addOp);
                    break;
                }
                case ENVIRONMENT_VARIABLES: {
                    if (hasEnvironmentVariables) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                    }
                    parseEnvironmentVariables(reader, expectedNs, addOp);
                    hasEnvironmentVariables = true;
                    break;
                }
                case JVM_OPTIONS: {
                    if (hasJvmOptions) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                    }
                    parseJvmOptions(reader, expectedNs, addOp);
                    hasJvmOptions = true;
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private static void parseJvm_3_0(final XMLExtendedStreamReader reader, final ModelNode parentAddress, final String expectedNs, final List<ModelNode> updates,
            final Set<String> jvmNames, final boolean server) throws XMLStreamException {
        ModelNode addOp = Util.createAddOperation();

        String name = parseJvmAttributes(reader, addOp, jvmNames, server);

        final ModelNode address = parentAddress.clone();
        address.add(ModelDescriptionConstants.JVM, name);
        addOp.get(OP_ADDR).set(address);
        updates.add(addOp);

        // Handle elements
        boolean hasJvmOptions = false;
        boolean hasEnvironmentVariables = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case HEAP: {
                    parseHeap(reader, addOp);
                    break;
                }
                case PERMGEN: {
                    parsePermgen(reader, addOp);
                    break;
                }
                case STACK: {
                    parseStack(reader, addOp);
                    break;
                }
                case AGENT_LIB: {
                    parseAgentLib(reader, addOp);
                    break;
                }
                case AGENT_PATH: {
                    parseAgentPath(reader, addOp);
                    break;
                }
                case JAVA_AGENT: {
                    parseJavaagent(reader, addOp);
                    break;
                }
                case ENVIRONMENT_VARIABLES: {
                    if (hasEnvironmentVariables) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                    }
                    parseEnvironmentVariables(reader, expectedNs, addOp);
                    hasEnvironmentVariables = true;
                    break;
                }
                case JVM_OPTIONS: {
                    if (hasJvmOptions) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                    }
                    parseJvmOptions(reader, expectedNs, addOp);
                    hasJvmOptions = true;
                    break;
                }
                case LAUNCH_COMMAND: {
                    parseLaunchCommand(reader, addOp);
                    break;
                }
                case MODULE_OPTIONS: {
                    parseModuleOptions(reader, addOp);
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    public static ModelNode parseEnvironmentVariables(final XMLExtendedStreamReader reader, final String expectedNs, ModelNode addOp) throws XMLStreamException {
        final ModelNode properties = new ModelNode();
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            if (Element.forName(reader.getLocalName()) != Element.VARIABLE) {
                throw unexpectedElement(reader);
            }
            final String[] array = requireAttributes(reader, Attribute.NAME.getLocalName(), Attribute.VALUE.getLocalName());
            requireNoContent(reader);
            properties.get(array[0]).set(ParseUtils.parsePossibleExpression(array[1]));
        }

        if (!properties.isDefined()) {
            throw missingRequiredElement(reader, Collections.singleton(Element.OPTION));
        }
        addOp.get(JvmAttributes.JVM_ENV_VARIABLES).set(properties);
        return properties;
    }


    private static void parseHeap(final XMLExtendedStreamReader reader, ModelNode addOp)
            throws XMLStreamException {
        // Handle attributes
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SIZE: {
                        if (checkParseAndSetParameter(JvmAttributes.HEAP_SIZE, value, addOp, reader)) {
                            throw ParseUtils.duplicateNamedElement(reader, reader.getLocalName());
                        }
                        break;
                    }
                    case MAX_SIZE: {
                        if (checkParseAndSetParameter(JvmAttributes.MAX_HEAP_SIZE, value, addOp, reader)) {
                            throw ParseUtils.duplicateNamedElement(reader, reader.getLocalName());
                        }
                        break;
                    }
                    default:
                        throw ParseUtils.unexpectedAttribute(reader, i);
                }
            }
        }

        // Handle elements
        requireNoContent(reader);
    }

    private static void parsePermgen(final XMLExtendedStreamReader reader, ModelNode addOp)
            throws XMLStreamException {

        // Handle attributes
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SIZE: {
                        if (checkParseAndSetParameter(JvmAttributes.PERMGEN_SIZE, value, addOp, reader)) {
                            throw ParseUtils.duplicateNamedElement(reader, reader.getLocalName());
                        }
                        break;
                    }
                    case MAX_SIZE: {
                        if (checkParseAndSetParameter(JvmAttributes.MAX_PERMGEN_SIZE, value, addOp, reader)) {
                            throw ParseUtils.duplicateNamedElement(reader, reader.getLocalName());
                        }
                        break;
                    }
                    default:
                        throw ParseUtils.unexpectedAttribute(reader, i);
                }
            }
        }

        // Handle elements
        requireNoContent(reader);
    }

    private static void parseStack(final XMLExtendedStreamReader reader, ModelNode addOp)
            throws XMLStreamException {

        // Handle attributes
        boolean sizeSet = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case SIZE: {
                        sizeSet = true;
                        if (checkParseAndSetParameter(JvmAttributes.STACK_SIZE, value, addOp, reader)){
                            throw ParseUtils.duplicateNamedElement(reader, reader.getLocalName());

                        }
                        break;
                    }
                    default:
                        throw ParseUtils.unexpectedAttribute(reader, i);
                }
            }
        }
        if (!sizeSet) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.SIZE));
        }
        // Handle elements
        requireNoContent(reader);
    }

    private static void parseAgentLib(final XMLExtendedStreamReader reader, ModelNode addOp)
            throws XMLStreamException {

        // Handle attributes
        boolean valueSet = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case VALUE: {
                        if (checkParseAndSetParameter(JvmAttributes.AGENT_LIB, value, addOp, reader)){
                            throw ParseUtils.duplicateNamedElement(reader, reader.getLocalName());
                        }
                        valueSet = true;
                        break;
                    }
                    default:
                        throw ParseUtils.unexpectedAttribute(reader, i);
                }
            }
        }
        if (!valueSet) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.VALUE));
        }
        // Handle elements
        requireNoContent(reader);
    }

    private static void parseAgentPath(final XMLExtendedStreamReader reader, ModelNode addOp)
            throws XMLStreamException {

        // Handle attributes
        boolean valueSet = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case VALUE: {
                        if (checkParseAndSetParameter(JvmAttributes.AGENT_PATH, value, addOp, reader)){
                            throw ParseUtils.duplicateNamedElement(reader, reader.getLocalName());
                        }
                        valueSet = true;
                        break;
                    }
                    default:
                        throw ParseUtils.unexpectedAttribute(reader, i);
                }
            }
        }
        if (!valueSet) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.VALUE));
        }
        // Handle elements
        requireNoContent(reader);
    }

    private static void parseLaunchCommand(final XMLExtendedStreamReader reader, ModelNode addOp)
            throws XMLStreamException {

        // Handle attributes
        boolean valueSet = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case PREFIX: {
                        if (checkParseAndSetParameter(JvmAttributes.LAUNCH_COMMAND, value, addOp, reader)) {
                            throw ParseUtils.duplicateNamedElement(reader, reader.getLocalName());
                        }
                        valueSet = true;
                        break;
                    }
                    default:
                        throw ParseUtils.unexpectedAttribute(reader, i);
                }
            }
        }
        if (!valueSet) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.PREFIX));
        }
        // Handle elements
        requireNoContent(reader);
    }

    private static void parseJavaagent(final XMLExtendedStreamReader reader, ModelNode addOp)
            throws XMLStreamException {

        // Handle attributes
        boolean valueSet = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case VALUE: {
                        if (checkParseAndSetParameter(JvmAttributes.JAVA_AGENT, value, addOp, reader)) {
                            throw ParseUtils.duplicateNamedElement(reader, reader.getLocalName());
                        }
                        valueSet = true;
                        break;
                    }
                    default:
                        throw ParseUtils.unexpectedAttribute(reader, i);
                }
            }
        }
        if (!valueSet) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.VALUE));
        }
        // Handle elements
        requireNoContent(reader);
    }

    private static void parseJvmOptions(final XMLExtendedStreamReader reader, final String expectedNs, final ModelNode addOp)
            throws XMLStreamException {

        ModelNode options = new ModelNode();
        // Handle attributes
        ParseUtils.requireNoAttributes(reader);
        // Handle elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            if (element == Element.OPTION) {
                // Handle attributes
                ModelNode option = null;
                final int count = reader.getAttributeCount();
                for (int i = 0; i < count; i++) {
                    final String attrValue = reader.getAttributeValue(i);
                    if (!isNoNamespaceAttribute(reader, i)) {
                        throw ParseUtils.unexpectedAttribute(reader, i);
                    } else {
                        final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                        switch (attribute) {
                            case VALUE: {
                                option = ParseUtils.parsePossibleExpression(attrValue);
                                break;
                            }
                            default:
                                throw ParseUtils.unexpectedAttribute(reader, i);
                        }
                    }
                }
                if (option == null) {
                    throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.NAME));
                }

                options.add(option);
                // Handle elements
                requireNoContent(reader);
            } else {
                throw unexpectedElement(reader);
            }
        }
        if (!options.isDefined()) {
            throw missingRequiredElement(reader, Collections.singleton(Element.OPTION));
        }
        addOp.get(JvmAttributes.JVM_OPTIONS).set(options);
    }

    static void parseModuleOptions(final XMLExtendedStreamReader reader, final ModelNode addOp) throws XMLStreamException {

        final ModelNode options = new ModelNode();
        // Handle attributes
        ParseUtils.requireNoAttributes(reader);
        // Handle elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element moduleOptionElement = Element.forName(reader.getLocalName());
            if (moduleOptionElement == Element.OPTION) {
                // Handle attributes
                ModelNode option = null;
                final int count = reader.getAttributeCount();
                for (int i = 0; i < count; i++) {
                    final String attrValue = reader.getAttributeValue(i);
                    if (!isNoNamespaceAttribute(reader, i)) {
                        throw ParseUtils.unexpectedAttribute(reader, i);
                    } else {
                        final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                        if (attribute == Attribute.VALUE) {
                            option = ParseUtils.parsePossibleExpression(attrValue);
                        } else {
                            throw ParseUtils.unexpectedAttribute(reader, i);
                        }
                    }
                }
                if (option == null) {
                    throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.VALUE));
                }

                options.add(option);
                // Handle elements
                requireNoContent(reader);
            } else {
                throw unexpectedElement(reader);
            }
        }
        if (!options.isDefined()) {
            throw missingRequiredElement(reader, Collections.singleton(Element.OPTION));
        }
        addOp.get(JvmAttributes.MODULE_OPTIONS.getName()).set(options);
    }

    public static void writeJVMElement(final XMLExtendedStreamWriter writer, final String jvmName, final ModelNode jvmElement)
            throws XMLStreamException {
        writer.writeStartElement(Element.JVM.getLocalName());
        writer.writeAttribute(Attribute.NAME.getLocalName(), jvmName);

        JvmAttributes.TYPE.marshallAsAttribute(jvmElement, writer);
        JvmAttributes.JAVA_HOME.marshallAsAttribute(jvmElement, writer);
        JvmAttributes.DEBUG_ENABLED.marshallAsAttribute(jvmElement, writer);
        JvmAttributes.DEBUG_OPTIONS.marshallAsAttribute(jvmElement, writer);
        if (JvmAttributes.DEBUG_OPTIONS.isMarshallable(jvmElement)) {
            if (!JvmAttributes.DEBUG_ENABLED.isMarshallable(jvmElement)) {
                writer.writeAttribute(Attribute.DEBUG_ENABLED.getLocalName(), "false");
            }
        }
        JvmAttributes.ENV_CLASSPATH_IGNORED.marshallAsAttribute(jvmElement, writer);
        if (JvmAttributes.HEAP_SIZE.isMarshallable(jvmElement) || JvmAttributes.MAX_HEAP_SIZE.isMarshallable(jvmElement)) {
            writer.writeEmptyElement(Element.HEAP.getLocalName());
            JvmAttributes.HEAP_SIZE.marshallAsAttribute(jvmElement, writer);
            JvmAttributes.MAX_HEAP_SIZE.marshallAsAttribute(jvmElement, writer);
        }
        if (JvmAttributes.PERMGEN_SIZE.isMarshallable(jvmElement) || JvmAttributes.MAX_PERMGEN_SIZE.isMarshallable(jvmElement)) {
            writer.writeEmptyElement(Element.PERMGEN.getLocalName());
            JvmAttributes.PERMGEN_SIZE.marshallAsAttribute(jvmElement, writer);
            JvmAttributes.MAX_PERMGEN_SIZE.marshallAsAttribute(jvmElement, writer);
        }
        if (JvmAttributes.STACK_SIZE.isMarshallable(jvmElement)) {
            writer.writeEmptyElement(Element.STACK.getLocalName());
            JvmAttributes.STACK_SIZE.marshallAsAttribute(jvmElement, writer);
        }
        if (JvmAttributes.AGENT_LIB.isMarshallable(jvmElement)) {
            writer.writeEmptyElement(Element.AGENT_LIB.getLocalName());
            JvmAttributes.AGENT_LIB.marshallAsAttribute(jvmElement, writer);
        }
        if (JvmAttributes.AGENT_PATH.isMarshallable(jvmElement)) {
            writer.writeEmptyElement(Element.AGENT_PATH.getLocalName());
            JvmAttributes.AGENT_PATH.marshallAsAttribute(jvmElement, writer);
        }
        if (JvmAttributes.JAVA_AGENT.isMarshallable(jvmElement)) {
            writer.writeEmptyElement(Element.JAVA_AGENT.getLocalName());
            JvmAttributes.JAVA_AGENT.marshallAsAttribute(jvmElement, writer);
        }
        if (JvmAttributes.OPTIONS.isMarshallable(jvmElement)) {
            JvmAttributes.OPTIONS.marshallAsElement(jvmElement, writer);
        }
        if (JvmAttributes.ENVIRONMENT_VARIABLES.isMarshallable(jvmElement)) {
            JvmAttributes.ENVIRONMENT_VARIABLES.marshallAsElement(jvmElement, writer);
        }
        if (JvmAttributes.LAUNCH_COMMAND.isMarshallable(jvmElement)) {
            writer.writeEmptyElement(Element.LAUNCH_COMMAND.getLocalName());
            JvmAttributes.PREFIX.marshallAsAttribute(jvmElement, writer);
        }

        if (JvmAttributes.MODULE_OPTIONS.isMarshallable(jvmElement)) {
            JvmAttributes.MODULE_OPTIONS.marshallAsElement(jvmElement, writer);
        }

        writer.writeEndElement();
    }

    private static boolean checkParseAndSetParameter(final SimpleAttributeDefinition ad, final String value, final ModelNode operation, final XMLStreamReader reader) throws XMLStreamException {
        boolean alreadyExisted = operation.hasDefined(ad.getName());
        ad.parseAndSetParameter(value, operation, reader);
        return alreadyExisted;
    }
}
