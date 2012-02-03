/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.plugin.deployment.resource;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.plugin.deployment.common.AbstractServerConnection;
import org.jboss.dmr.ModelNode;

/**
 * Adds a resource
 * <p/>
 * If {@code force} is set to {@code false} and the resource has already been deployed to the server, an error will
 * occur and the operation will fail.
 *
 * @author Stuart Douglas
 * @goal add-resource
 */
public class AddResource extends AbstractServerConnection {

    public static final String GOAL = "add-resource";

    /**
     * The operation address, as a comma separated string.
     * <p/>
     * If the resource or resources also define and address, this address will be used as the parent address. Meaning
     * the resource addresses will be prepended with this address.
     *
     * @parameter
     */
    private String address;

    /**
     * The operation properties.
     *
     * @parameter
     * @deprecated prefer the {@code resources} or {@code resource} configuration.
     */
    private Map<String, String> properties;

    /**
     * The resource to add.
     * <p/>
     * A resource could consist of; <ul> <li>An address, which may be appended to this address if defined {@literal
     * <address/>}.</li> <li>A mapping of properties to be set on the resource {@literal <properties/>}.</li> <li>A flag
     * to indicate whether or not the resource should be enabled {@literal <enableResource/>}</li> </ul>
     *
     * @parameter
     */
    private Resource resource;

    /**
     * A collection of resources to add.
     *
     * @parameter
     */
    private Resource[] resources;

    /**
     * Specifies whether force mode should be used or not. </p> If force mode is disabled, the add-resource goal will
     * cause a build failure if the resource is already present on the server
     *
     * @parameter default-value="true" expression="${add-resource.force}"
     */
    private boolean force;

    @Override
    public String goal() {
        return GOAL;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            final InetAddress host = hostAddress();
            getLog().info(String.format("Executing goal %s on server %s (%s) port %s.", goal(), host.getHostName(), host.getHostAddress(), port()));
            final ModelControllerClient client = client();
            try {
                if (resources == null) {
                    final Resource resource = (this.resource == null ? new Resource(address, properties, false) : this.resource);
                    processResources(client, resource);
                } else {
                    if (resources.length > 0) {
                        processResources(client, resources);
                    } else {
                        getLog().warn("No resources were provided.");
                    }
                }
            } finally {
                safeCloseClient();
            }
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not execute goal %s. Reason: %s", goal(), e.getMessage()), e);
        }
    }

    private void processResources(final ModelControllerClient client, final Resource... resources) throws IOException {
        for (Resource resource : resources) {
            final ModelNode op = new ModelNode();
            op.get(ClientConstants.OP).set(ClientConstants.COMPOSITE);
            op.get(ClientConstants.OP_ADDR).setEmptyList();
            op.get(ClientConstants.ROLLBACK_ON_RUNTIME_FAILURE).set(true);
            if (addCompositeResource(client, resource, op, true)) {
                ModelNode r = client.execute(OperationBuilder.create(op).build());
                reportFailure(r);
            }
        }
    }

    private boolean addCompositeResource(final ModelControllerClient client, final Resource resource, final ModelNode compositeOp, final boolean checkExistence) throws IOException {
        final String address;
        if (this.address == null) {
            address = resource.getAddress();
        } else if (this.address.equals(resource.getAddress())) {
            address = resource.getAddress();
        } else if (resource.getAddress() == null) {
            address = this.address;
        } else {
            address = String.format("%s,%s", this.address, resource.getAddress());
        }
        // The address cannot be null
        if (address == null) {
            throw new RuntimeException("You must specify the address to deploy the resource to.");
        }
        if (checkExistence) {
            final boolean exists = resourceExists(address, client);
            if (resource.isAddIfAbsent() && exists) {
                return false;
            }
            if (exists && force) {
                ModelNode r = client.execute(OperationBuilder.create(buildRemoveOperation(address)).build());
                reportFailure(r);
            } else if (exists && !force) {
                throw new RuntimeException("Resource " + address + " already exists.");
            }
        }
        compositeOp.get(ClientConstants.STEPS).add(buildAddOperation(address, resource.getProperties()));
        if (resource.getResources() != null) {
            for (Resource r : resource.getResources()) {
                addCompositeResource(client, r, compositeOp, false);
            }
        }
        if (resource.isEnableResource()) {
            compositeOp.get(ClientConstants.STEPS).add(buildEnableOperation(address));
        }
        return true;
    }

    /**
     * Creates the operation to remove a resource.
     *
     * @param address the address of the resource to remove.
     *
     * @return the operation.
     */
    private ModelNode buildRemoveOperation(final String address) {
        //we need to remove the datasource
        final ModelNode op = new ModelNode();
        op.get(ClientConstants.OP).set("remove");
        op.get("recursive").set(true);
        setupAddress(address, op);
        return op;
    }

    /**
     * Creates the operation to enable a resource.
     *
     * @param address the address of the resource.
     *
     * @return the operation.
     */
    private ModelNode buildEnableOperation(final String address) {
        final ModelNode op = new ModelNode();
        op.get(ClientConstants.OP).set("enable");
        setupAddress(address, op);
        return op;
    }

    /**
     * Creates the operation to add a resource.
     *
     * @param address    the address of the operation to add.
     * @param properties the properties to set for the resource.
     *
     * @return the operation.
     */
    private ModelNode buildAddOperation(final String address, final Map<String, String> properties) {
        final ModelNode op = new ModelNode();
        op.get(ClientConstants.OP).set(ClientConstants.ADD);
        setupAddress(address, op);
        for (Map.Entry<String, String> prop : properties.entrySet()) {
            final String[] props = prop.getKey().split(",");
            if (props.length == 0) {
                throw new RuntimeException("Invalid property " + prop);
            }
            ModelNode node = op;
            for (int i = 0; i < props.length - 1; ++i) {
                node = node.get(props[i]);
            }
            final String value = prop.getValue() == null ? "" : prop.getValue();
            if (value.startsWith("!!")) {
                handleDmrString(node, props[props.length - 1], value);
            } else {
                node.get(props[props.length - 1]).set(value);
            }
        }
        return op;
    }

    /**
     * Checks the existence of a resource. If the resource exists, {@code true} is returned, otherwise {@code false}.
     *
     * @param address the address of the resource to check.
     * @param client  the client used to execute the operation.
     *
     * @return {@code true} if the resources exists, otherwise {@code false}.
     *
     * @throws IOException      if an error occurs executing the operation.
     * @throws RuntimeException if the operation fails.
     */
    private boolean resourceExists(final String address, final ModelControllerClient client) throws IOException {
        //first we check if the resource already exists
        ModelNode request = new ModelNode();
        request.get(ClientConstants.OP).set("read-resource");
        request.get("recursive").set(false);
        AddressPair childAddress = setupParentAddress(address, request);

        ModelNode r = client.execute(new OperationBuilder(request).build());
        reportFailure(r);
        boolean found = false;
        if (r.get(ClientConstants.RESULT).get(childAddress.type).isDefined()) {
            for (ModelNode dataSource : r.get(ClientConstants.RESULT).get(childAddress.type).asList()) {
                if (dataSource.asProperty().getName().equals(childAddress.name)) {
                    found = true;
                }
            }
        }
        return found;
    }

    /**
     * Handles DMR strings in the configuration
     */
    private void handleDmrString(final ModelNode node, final String name, final String value) {
        final String realValue = value.substring(2);
        node.get(name).set(ModelNode.fromString(realValue));
    }

    private void setupAddress(String inputAddress, final ModelNode request) {
        String[] parts = inputAddress.split(",");
        for (String part : parts) {
            String[] address = part.split("=");
            if (address.length != 2) {
                throw new RuntimeException(part + " is not a valid address segment");
            }
            request.get(ClientConstants.OP_ADDR).add(address[0], address[1]);
        }
    }

    private void reportFailure(final ModelNode node) {
        if (!node.get(ClientConstants.OUTCOME).asString().equals(ClientConstants.SUCCESS)) {
            final String msg;
            if (node.hasDefined(ClientConstants.FAILURE_DESCRIPTION)) {
                if (node.hasDefined(ClientConstants.OP)) {
                    msg = String.format("Operation '%s' at address '%s' failed: %s", node.get(ClientConstants.OP), node.get(ClientConstants.OP_ADDR), node.get(ClientConstants.FAILURE_DESCRIPTION));
                } else {
                    msg = String.format("Operation failed: %s", node.get(ClientConstants.FAILURE_DESCRIPTION));
                }
            } else {
                msg = String.format("Operation failed: %s", node);
            }
            throw new RuntimeException(msg);
        }
    }

    /**
     * Adds the parent address to the model node and returns the details of the last element in the address
     *
     * @param address the address to set-up the parent address for.
     * @param request the request node.
     *
     * @return the address pair.
     */
    private AddressPair setupParentAddress(final String address, final ModelNode request) {
        String[] parts = address.split(",");
        for (int i = 0; i < parts.length - 1; ++i) {
            String part = parts[i];
            String[] addressParts = part.split("=");
            if (addressParts.length != 2) {
                throw new RuntimeException(part + " is not a valid address segment");
            }
            request.get(ClientConstants.OP_ADDR).add(addressParts[0], addressParts[1]);
        }
        String part = parts[parts.length - 1];
        String[] addressParts = part.split("=");
        if (addressParts.length != 2) {
            throw new RuntimeException(part + " is not a valid address segment");
        }
        return new AddressPair(addressParts[0], addressParts[1]);
    }


    private static class AddressPair {
        private final String name;
        private final String type;

        public AddressPair(final String type, final String name) {
            this.name = name;
            this.type = type;
        }
    }
}
