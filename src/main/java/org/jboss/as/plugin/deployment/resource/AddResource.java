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
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.plugin.common.AbstractServerConnection;
import org.jboss.as.plugin.common.Operations;
import org.jboss.as.plugin.common.Operations.CompositeOperationBuilder;
import org.jboss.as.plugin.common.PropertyNames;
import org.jboss.as.plugin.deployment.domain.Domain;
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
 */
@Mojo(name = "add-resource", threadSafe = true)
public class AddResource extends AbstractServerConnection {

    public static final String GOAL = "add-resource";

    /**
     * Specifies the configuration for a domain server.
     */
    @Parameter
    private Domain domain;

    /**
     * The operation address, as a comma separated string.
     * <p/>
     * If the resource or resources also define and address, this address will be used as the parent address. Meaning
     * the resource addresses will be prepended with this address.
     */
    @Parameter
    private String address;

    /**
     * The operation properties.
     *
     * @deprecated prefer the {@code resources} or {@code resource} configuration.
     */
    @Parameter
    @Deprecated
    private Map<String, String> properties;

    /**
     * The resource to add.
     * <p/>
     * A resource could consist of;
     * <ul>
     * <li>An address, which may be appended to this address if defined {@literal <address/>}.
     * </li>
     * <li>A mapping of properties to be set on the resource {@literal <properties/>}.</li>
     * <li>A flag to indicate whether or not the resource should be enabled {@literal <enableResource/>}</li>
     * </ul>
     */
    @Parameter
    private Resource resource;

    /**
     * A collection of resources to add.
     */
    @Parameter
    private Resource[] resources;

    /**
     * Specifies whether force mode should be used or not.
     * </p>
     * If force mode is disabled, the add-resource goal will
     * cause a build failure if the resource is already present on the server.
     */
    @Parameter(defaultValue = "true", property = PropertyNames.ADD_RESOURCE_FORCE)
    private boolean force;

    /**
     * Set to {@code true} if you want the deployment to be skipped, otherwise {@code false}.
     */
    @Parameter(defaultValue = "false")
    private boolean skip;

    @Override
    public String goal() {
        return GOAL;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping add-resource with address %s", address));
            return;
        }
        try {
            final InetAddress host = getHostAddress();
            getLog().info(String.format("Executing goal %s on server %s (%s) port %s.", goal(), host.getHostName(), host.getHostAddress(), getPort()));
            synchronized (CLIENT_LOCK) {
                final ModelControllerClient client = getClient();
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
            }
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not execute goal %s. Reason: %s", goal(), e.getMessage()), e);
        } finally {
            close();
        }
    }

    private void processResources(final ModelControllerClient client, final Resource... resources) throws IOException {
        for (Resource resource : resources) {
            if (isDomainServer()) {
                // Profiles are required when adding resources in domain mode
                final List<String> profiles = domain.getProfiles();
                if (profiles.isEmpty()) {
                    throw new IllegalStateException("Cannot add resources when no profiles were defined.");
                }
                for (String profile : profiles) {
                    final CompositeOperationBuilder compositeOperationBuilder = CompositeOperationBuilder.create();
                    if (addCompositeResource(profile, client, resource, address, compositeOperationBuilder, true)) {
                        if (resource.hasBeforeAddCommands()) {
                            resource.getBeforeAdd().execute(client);
                        }
                        // Execute the add resource operation
                        reportFailure(client.execute(compositeOperationBuilder.build()));

                        if (resource.hasAfterAddCommands()) {
                            resource.getAfterAdd().execute(client);
                        }
                    }
                }
            } else {
                final CompositeOperationBuilder compositeOperationBuilder = CompositeOperationBuilder.create();
                if (addCompositeResource(null, client, resource, address, compositeOperationBuilder, true)) {
                    if (resource.hasBeforeAddCommands()) {
                        resource.getBeforeAdd().execute(client);
                    }
                    // Execute the add resource operation
                    reportFailure(client.execute(compositeOperationBuilder.build()));

                    if (resource.hasAfterAddCommands()) {
                        resource.getAfterAdd().execute(client);
                    }
                }
            }
        }
    }

    private boolean addCompositeResource(final String profileName, final ModelControllerClient client, final Resource resource, final String parentAddress, final CompositeOperationBuilder compositeOp, final boolean checkExistence) throws IOException {
        final String inputAddress;
        if (parentAddress == null) {
            inputAddress = resource.getAddress();
        } else if (parentAddress.equals(resource.getAddress())) {
            inputAddress = resource.getAddress();
        } else if (resource.getAddress() == null) {
            inputAddress = parentAddress;
        } else {
            inputAddress = String.format("%s,%s", parentAddress, resource.getAddress());
        }
        // The address cannot be null
        if (inputAddress == null) {
            throw new RuntimeException("You must specify the address to deploy the resource to.");
        }
        final ModelNode address = parseAddress(profileName, inputAddress);
        if (checkExistence) {
            final boolean exists = resourceExists(address, client);
            if (resource.isAddIfAbsent() && exists) {
                return false;
            }
            if (exists && force) {
                reportFailure(client.execute(Operations.createRemoveOperation(address, true)));
            } else if (exists && !force) {
                throw new RuntimeException(String.format("Resource %s already exists.", address));
            }
        }
        compositeOp.addStep(buildAddOperation(address, resource.getProperties()));
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
            compositeOp.addStep(Operations.createOperation(Operations.ENABLE, address));
        }
        return true;
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
        final ModelNode op = Operations.createAddOperation(address);
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
        final Property childAddress = Operations.getChildAddress(address);
        final ModelNode parentAddress = Operations.getParentAddress(address);
        final ModelNode r = client.execute(Operations.createOperation(Operations.READ_RESOURCE, parentAddress, false));
        reportFailure(r);
        boolean found = false;
        final String name = childAddress.getName();
        if (r.get(Operations.RESULT).get(name).isDefined()) {
            for (ModelNode dataSource : r.get(Operations.RESULT).get(name).asList()) {
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
     * Parses the comma delimited address into model nodes.
     *
     * @param profileName  the profile name for the domain or {@code null} if not a domain
     * @param inputAddress the address.
     *
     * @return a collection of the address nodes.
     */
    private ModelNode parseAddress(final String profileName, final String inputAddress) {
        final ModelNode result = new ModelNode();
        if (profileName != null) {
            result.add(Operations.PROFILE, profileName);
        }
        String[] parts = inputAddress.split(",");
        for (String part : parts) {
            String[] address = part.split("=");
            if (address.length != 2) {
                throw new RuntimeException(part + " is not a valid address segment");
            }
            result.add(address[0], address[1]);
        }
        return result;
    }

    private void reportFailure(final ModelNode result) {
        if (!Operations.successful(result)) {
            throw new RuntimeException(Operations.getFailureDescription(result));
        }
    }
}
