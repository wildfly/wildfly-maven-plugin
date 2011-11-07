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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.plugin.deployment.common.AbstractServerConnection;
import org.jboss.dmr.ModelNode;

import java.net.InetAddress;
import java.util.Map;

/**
 * Adds a resource
 * <p/>
 * If {@code force} is set to {@code false} and the resource has already been deployed to the server, an error
 * will occur and the operation will fail.
 *
 * @author Stuart Douglas
 * @goal add-resource
 */
public class AddResource extends AbstractServerConnection {

    public static final String GOAL = "add-resource";

    /**
     * The operation address, as a comma separated string
     *
     * @parameter
     */
    private String address;

    /**
     * The operation properties
     *
     * @parameter
     */
    private Map<String, String> properties;

    /**
     * Specifies whether force mode should be used or not.
     * </p>
     * If force mode is disabled, the add-resource goal will cause a build failure if the resource is already present on the server
     *
     * @parameter default-value="true"
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

            //first we check if the resource already exists
            ModelNode request = new ModelNode();
            request.get(ClientConstants.OP).set("read-resource");
            request.get("recursive").set(false);
            AddressPair childAddress = setupParentAddress(request);

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


            if (found && force) {
                //we need to remove the datasource
                request = new ModelNode();
                request.get(ClientConstants.OP).set("remove");
                setupAddress(request);
                r = client.execute(new OperationBuilder(request).build());
                reportFailure(r);

            } else if (found && !force) {
                throw new RuntimeException("Resource " + address + " already exists ");
            }
            request = new ModelNode();
            request.get(ClientConstants.OP).set(ClientConstants.ADD);
            setupAddress(request);
            for (Map.Entry<String, String> prop : properties.entrySet()) {
                final String[] props = prop.getKey().split(",");
                if (props.length == 0) {
                    throw new RuntimeException("Invalid property " + prop);
                }
                ModelNode node = request;
                for (int i = 0; i < props.length - 1; ++i) {
                    node = node.get(props[i]);
                }
                final String value = prop.getValue() == null ? "" : prop.getValue();
                if(value.startsWith("!!")) {
                    handleDmrString(node, props[props.length - 1], value);
                } else {
                    node.get(props[props.length - 1]).set(value);
                }
            }
            r = client.execute(new OperationBuilder(request).build());
            reportFailure(r);
        } catch (Exception e) {
            throw new MojoExecutionException(String.format("Could not execute goal %s. Reason: %s", goal(), e.getMessage()), e);
        }
    }

    /**
     * Handles DMR strings in the configuration
     */
    private void handleDmrString(final ModelNode node, final String name, final String value) {
        final String realValue = value.substring(2);
        node.get(name).set(ModelNode.fromString(realValue));
    }

    private void setupAddress(final ModelNode request) {
        String[] parts = address.split(",");
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
            throw new RuntimeException("Operation failed " + node);
        }
    }

    /**
     * Adds the parent address to the model node and returns the details of the last element in the address
     */
    private AddressPair setupParentAddress(final ModelNode request) {
        String[] parts = address.split(",");
        for (int i = 0; i < parts.length - 1; ++i) {
            String part = parts[i];
            String[] address = part.split("=");
            if (address.length != 2) {
                throw new RuntimeException(part + " is not a valid address segment");
            }
            request.get(ClientConstants.OP_ADDR).add(address[0], address[1]);
        }
        String part = parts[parts.length - 1];
        String[] address = part.split("=");
        if (address.length != 2) {
            throw new RuntimeException(part + " is not a valid address segment");
        }
        return new AddressPair(address[0], address[1]);
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
