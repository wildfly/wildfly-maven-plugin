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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.plugin.common.AbstractServerConnection;
import org.jboss.as.plugin.common.Streams;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Adds a resource
 * <p/>
 * If {@code force} is set to {@code false} and the resource has already been deployed to the server, an error will
 * occur and the operation will fail.
 * <p/>
 * <b>Note:</b> this currently only works with adding resources to subsystems when your server is running in domain
 * mode.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @goal add-resource
 */
public class AddResource extends AbstractServerConnection {

    public static final String GOAL = "add-resource";
    public static final String PROFILE = "profile";

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
     * <address/>}.</li> <li>A mapping of properties to be set on the resource {@literal <properties/>}.</li> <li>A
     * flag
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
            final InetAddress host = getHostAddress();
            getLog().info(String.format("Executing goal %s on server %s (%s) port %s.", goal(), host.getHostName(), host.getHostAddress(), getPort()));
            final ModelControllerClient client = ModelControllerClient.Factory.create(getHostAddress(), getPort(), getCallbackHandler());
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
                Streams.safeClose(client);
            }
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not execute goal %s. Reason: %s", goal(), e.getMessage()), e);
        }
    }

    private void processResources(final ModelControllerClient client, final Resource... resources) throws IOException {
        final ModelNode op = new ModelNode();
        op.get(ClientConstants.OP).set(ClientConstants.COMPOSITE);
        op.get(ClientConstants.OP_ADDR).setEmptyList();
        op.get(ClientConstants.ROLLBACK_ON_RUNTIME_FAILURE).set(true);
        for (Resource resource : resources) {
            if (isDomainServer()) {
                // Profiles are required when adding resources in domain mode
                final List<String> profiles = getDomain().getProfiles();
                if (profiles.isEmpty()) {
                    throw new IllegalStateException("Cannot add resources when no profiles were defined.");
                }
                for (String profile : profiles) {
                    final ModelNode model = new ModelNode();
                    if (addCompositeResource(profile, client, resource, address, model, true)) {
                        op.get(ClientConstants.STEPS).set(model.get(ClientConstants.STEPS));
                        ModelNode r = client.execute(OperationBuilder.create(op).build());
                        reportFailure(r);
                    }
                }
            } else {
                final ModelNode model = new ModelNode();
                if (addCompositeResource(null, client, resource, address, model, true)) {
                    op.get(ClientConstants.STEPS).set(model.get(ClientConstants.STEPS));
                    ModelNode r = client.execute(OperationBuilder.create(op).build());
                    reportFailure(r);
                }
            }
        }
    }

    private boolean addCompositeResource(final String profileName, final ModelControllerClient client, final Resource resource, final String parentAddress, final ModelNode compositeOp, final boolean checkExistence) throws IOException {
        final ModelNode address = new ModelNode();
        if (parentAddress == null) {
            setupAddress(profileName, resource.getAddress(), address);
        } else if (parentAddress.equals(resource.getAddress())) {
            setupAddress(profileName, resource.getAddress(), address);
        } else if (resource.getAddress() == null) {
            setupAddress(profileName, parentAddress, address);
        } else {
            setupAddress(profileName, String.format("%s,%s", parentAddress, resource.getAddress()), address);
        }
        // The address cannot be null
        if (!address.isDefined()) {
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
            final String resourceAddress = resource.getAddress();
            final String addr;
            if (parentAddress != null && resourceAddress != null) {
                addr = parentAddress + "," + resourceAddress;
            } else if (parentAddress != null) {
                addr = parentAddress;
            } else if (resourceAddress != null) {
                addr = resourceAddress;
            } else {
                addr = null;
            }
            for (Resource r : resource.getResources()) {
                addCompositeResource(profileName, client, r, addr, compositeOp, false);
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
    private ModelNode buildRemoveOperation(final ModelNode address) {
        //we need to remove the datasource
        final ModelNode op = new ModelNode();
        op.get(ClientConstants.OP).set("remove");
        op.get("recursive").set(true);
        op.get(ClientConstants.OP_ADDR).set(address.get(ClientConstants.OP_ADDR));
        return op;
    }

    /**
     * Creates the operation to enable a resource.
     *
     * @param address the address of the resource.
     *
     * @return the operation.
     */
    private ModelNode buildEnableOperation(final ModelNode address) {
        final ModelNode op = new ModelNode();
        op.get(ClientConstants.OP).set("enable");
        op.get(ClientConstants.OP_ADDR).set(address.get(ClientConstants.OP_ADDR));
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
    private ModelNode buildAddOperation(final ModelNode address, final Map<String, String> properties) {
        final ModelNode op = new ModelNode();
        op.get(ClientConstants.OP).set(ClientConstants.ADD);
        op.get(ClientConstants.OP_ADDR).set(address.get(ClientConstants.OP_ADDR));
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
    private boolean resourceExists(final ModelNode address, final ModelControllerClient client) throws IOException {
        //first we check if the resource already exists
        final ModelNode request = new ModelNode();
        request.get(ClientConstants.OP).set("read-resource");
        request.get("recursive").set(false);
        final Property childAddress = setupParentAddress(address, request);

        ModelNode r = client.execute(new OperationBuilder(request).build());
        reportFailure(r);
        boolean found = false;
        final String name = childAddress.getName();
        if (r.get(ClientConstants.RESULT).get(name).isDefined()) {
            for (ModelNode dataSource : r.get(ClientConstants.RESULT).get(name).asList()) {
                if (dataSource.asProperty().getName().equals(childAddress.getValue().asString())) {
                    found = true;
                }
            }
        }
        return found;
    }

    /**
     * Handles DMR strings in the configuration
     *
     * @param node  the node to create.
     * @param name  the name for the node.
     * @param value the value for the node.
     */
    private void handleDmrString(final ModelNode node, final String name, final String value) {
        final String realValue = value.substring(2);
        node.get(name).set(ModelNode.fromString(realValue));
    }

    /**
     * Set up the address.
     *
     * @param profileName  the profile, if required, where the resource should be added.
     * @param inputAddress the address to parse.
     * @param request      the request to add the address to.
     */
    private void setupAddress(final String profileName, final String inputAddress, final ModelNode request) {
        if (inputAddress != null) {
            if (profileName != null) {
                request.get(ClientConstants.OP_ADDR).add(PROFILE, profileName);
            }
            for (ModelNode part : parseAddressParts(inputAddress)) {
                request.get(ClientConstants.OP_ADDR).add(part);
            }
        }
    }

    /**
     * Parses the comma delimited address into model nodes.
     *
     * @param inputAddress the address.
     *
     * @return a collection of the address nodes.
     */
    private List<ModelNode> parseAddressParts(final String inputAddress) {
        String[] parts = inputAddress.split(",");
        final List<ModelNode> result = new ArrayList<ModelNode>(parts.length);
        for (String part : parts) {
            String[] address = part.split("=");
            if (address.length != 2) {
                throw new RuntimeException(part + " is not a valid address segment");
            }
            result.add(new ModelNode().set(address[0], address[1]));
        }
        return result;
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
    private Property setupParentAddress(final ModelNode address, final ModelNode request) {
        if (address.isDefined()) {
            final List<ModelNode> addresses = address.get(ClientConstants.OP_ADDR).asList();
            for (int i = 0; i < addresses.size() - 1; ++i) {
                request.get(ClientConstants.OP_ADDR).add(addresses.get(i));
            }
            return addresses.get(addresses.size() - 1).asProperty();
        }
        return null;
    }
}
