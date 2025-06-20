/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.DomainDeploymentManager;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.dmr.ModelNode;

/**
 * Domain client implementation.
 *
 * @author John Bailey
 */
public class DomainClientImpl implements DomainClient {

    private volatile DomainDeploymentManager deploymentManager;
    private final ModelControllerClient delegate;

    public DomainClientImpl(InetAddress address, int port) {
        this.delegate = ModelControllerClient.Factory.create(address, port);
    }

    public DomainClientImpl(InetAddress address, int port, CallbackHandler handler) {
        this.delegate = ModelControllerClient.Factory.create(address, port, handler);
    }

    public DomainClientImpl(String protocol, InetAddress address, int port) {
        this.delegate = ModelControllerClient.Factory.create(protocol, address, port);
    }

    public DomainClientImpl(String protocol, InetAddress address, int port, CallbackHandler handler) {
        this.delegate = ModelControllerClient.Factory.create(protocol, address, port, handler);
    }
    public DomainClientImpl(ModelControllerClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public ModelNode execute(ModelNode operation) throws IOException {
        return delegate.execute(operation);
    }

    @Override
    public ModelNode execute(Operation operation) throws IOException {
        return delegate.execute(operation);
    }

    @Override
    public ModelNode execute(ModelNode operation, OperationMessageHandler messageHandler) throws IOException {
        return delegate.execute(operation, messageHandler);
    }

    @Override
    public ModelNode execute(Operation operation, OperationMessageHandler messageHandler) throws IOException {
        return delegate.execute(operation, messageHandler);
    }

    @Override
    public CompletableFuture<ModelNode> executeAsync(ModelNode operation, OperationMessageHandler messageHandler) {
        return delegate.executeAsync(operation, messageHandler);
    }

    @Override
    public CompletableFuture<ModelNode> executeAsync(Operation operation, OperationMessageHandler messageHandler) {
        return delegate.executeAsync(operation, messageHandler);
    }

    @Override
    public OperationResponse executeOperation(Operation operation, OperationMessageHandler messageHandler) throws IOException {
        return delegate.executeOperation(operation, messageHandler);
    }

    @Override
    public CompletableFuture<OperationResponse> executeOperationAsync(Operation operation, OperationMessageHandler messageHandler) {
        return delegate.executeOperationAsync(operation, messageHandler);
    }

    @Override
    public byte[] addDeploymentContent(InputStream stream) {
        ModelNode op = new ModelNode();
        op.get("operation").set("upload-deployment-stream");
        op.get("input-stream-index").set(0);
        Operation operation = new OperationBuilder(op).addInputStream(stream).build();
        ModelNode result = executeForResult(operation);
        return result.asBytes();
    }

    @Override
    public DomainDeploymentManager getDeploymentManager() {
        if (deploymentManager == null) {
            synchronized (this) {
                if (deploymentManager == null) {
                    deploymentManager = new DomainDeploymentManagerImpl(this);
                }
            }
        }
        return deploymentManager;
    }

    @Override
    public List<String> getHostControllerNames() {
        ModelNode op = new ModelNode();
        op.get("operation").set("read-children-names");
        op.get("child-type").set("host");
        ModelNode result = executeForResult(new OperationBuilder(op).build());
        List<String> hosts = new ArrayList<String>();
        for (ModelNode host : result.asList()) {
            hosts.add(host.asString());
        }
        return hosts;
    }

    @Override
    public Map<ServerIdentity, ServerStatus> getServerStatuses() {
        Map<ServerIdentity, ServerStatus> result = new HashMap<ServerIdentity, ServerStatus>();
        List<String> hosts = getHostControllerNames();
        for (String host : hosts) {
            Set<String> servers = getServerNames(host);
            for (String server : servers) {
                ModelNode address = new ModelNode();
                address.add("host", host);
                address.add("server-config", server);
                String group = readAttribute("group", address).asString();
                ServerStatus status = Enum.valueOf(ServerStatus.class, readAttribute("status", address).asString());
                ServerIdentity id = new ServerIdentity(host, group, server);
                result.put(id, status);
            }

        }
        return result;
    }

    private Set<String> getServerNames(String host) {
        ModelNode op = new ModelNode();
        op.get("operation").set("read-children-names");
        op.get("child-type").set("server-config");
        op.get("address").add("host", host);
        ModelNode result = executeForResult(new OperationBuilder(op).build());
        Set<String> servers = new HashSet<String>();
        for (ModelNode server : result.asList()) {
            servers.add(server.asString());
        }
        return servers;
    }

    private ModelNode readAttribute(String name, ModelNode address) {
        ModelNode op = new ModelNode();
        op.get("operation").set("read-attribute");
        op.get("address").set(address);
        op.get("name").set(name);
        return executeForResult(new OperationBuilder(op).build());
    }

    @Override
    public ServerStatus startServer(String hostControllerName, String serverName) {

        final ModelNode op = new ModelNode();
        op.get("operation").set("start");
        ModelNode address = op.get("address");
        address.add("host", hostControllerName);
        address.add("server-config", serverName);
        ModelNode result = executeForResult(new OperationBuilder(op).build());
        String status = result.asString();
        return Enum.valueOf(ServerStatus.class, status);
    }

    @Override
    public ServerStatus stopServer(String hostControllerName, String serverName, long gracefulShutdownTimeout, TimeUnit timeUnit) {
//        long ms = gracefulShutdownTimeout < 0 ? - 1 : timeUnit.toMillis(gracefulShutdownTimeout);

        final ModelNode op = new ModelNode();
        op.get("operation").set("stop");
        ModelNode address = op.get("address");
        address.add("host", hostControllerName);
        address.add("server-config", serverName);
        // FIXME add graceful shutdown
        ModelNode result = executeForResult(new OperationBuilder(op).build());
        String status = result.asString();
        return Enum.valueOf(ServerStatus.class, status);
    }

    @Override
    public ServerStatus restartServer(String hostControllerName, String serverName, long gracefulShutdownTimeout, TimeUnit timeUnit) {
//        long ms = gracefulShutdownTimeout < 0 ? - 1 : timeUnit.toMillis(gracefulShutdownTimeout);

        final ModelNode op = new ModelNode();
        op.get("operation").set("restart");
        ModelNode address = op.get("address");
        address.add("host", hostControllerName);
        address.add("server-config", serverName);
        // FIXME add graceful shutdown
        ModelNode result = executeForResult(new OperationBuilder(op).build());
        String status = result.asString();
        return Enum.valueOf(ServerStatus.class, status);
    }

    boolean isDeploymentNameUnique(final String deploymentName) {
        final ModelNode op = new ModelNode();
        op.get("operation").set("read-children-names");
        op.get("child-type").set("deployment");
        final ModelNode result = executeForResult(new OperationBuilder(op).build());
        final Set<String> deploymentNames = new HashSet<String>();
        if (result.isDefined()) {
            for (ModelNode node : result.asList()) {
                deploymentNames.add(node.asString());
            }
        }
        return !deploymentNames.contains(deploymentName);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    ModelNode executeForResult(Operation op) {
        try {
            ModelNode result = delegate.execute(op);
            if (result.hasDefined("outcome") && "success".equals(result.get("outcome").asString())) {
                return result.get("result");
            }
            else if (result.hasDefined("failure-description")) {
                throw new RuntimeException(result.get("failure-description").toString());
            }
            else if (result.hasDefined("domain-failure-description")) {
                throw new RuntimeException(result.get("domain-failure-description").toString());
            }
            else if (result.hasDefined("host-failure-descriptions")) {
                throw new RuntimeException(result.get("host-failure-descriptions").toString());
            }
            else {
                throw ControllerClientLogger.ROOT_LOGGER.operationOutcome(result.get("outcome").asString());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
