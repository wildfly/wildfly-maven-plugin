/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.plugin.core;

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;

/**
 * A default implementation for the {@link ContainerDescription}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class DefaultContainerDescription implements ContainerDescription {

    private final String productName;
    private final String productVersion;
    private final String releaseVersion;
    private final String launchType;
    private final boolean isDomain;

    private DefaultContainerDescription(final String productName, final String productVersion,
            final String releaseVersion, final String launchType, final boolean isDomain) {
        this.productName = productName;
        this.productVersion = productVersion;
        this.releaseVersion = releaseVersion;
        this.launchType = launchType;
        this.isDomain = isDomain;
    }

    @Override
    public String getProductName() {
        return productName;
    }

    @Override
    public String getProductVersion() {
        return productVersion;
    }

    @Override
    public String getReleaseVersion() {
        return releaseVersion;
    }

    @Override
    public String getLaunchType() {
        return launchType;
    }

    @Override
    public boolean isDomain() {
        return isDomain;
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder(64);
        result.append(productName);
        if (productVersion != null) {
            result.append(' ').append(productVersion);
            if (releaseVersion != null) {
                result.append(" (WildFly Core ").append(releaseVersion).append(')');
            }
        } else {
            if (releaseVersion != null) {
                result.append(' ').append(releaseVersion);
            }
        }
        if (launchType != null) {
            result.append(" - launch-type: ").append(launchType);
        }
        return result.toString();
    }

    /**
     * Queries the running container and attempts to lookup the information from the running container.
     *
     * @param client the client used to execute the management operation
     *
     * @return the container description
     *
     * @throws IOException                 if an error occurs while executing the management operation
     * @throws OperationExecutionException if the operation used to query the container fails
     */
    static DefaultContainerDescription lookup(final ModelControllerClient client)
            throws IOException, OperationExecutionException {
        final ModelNode op = Operations.createReadResourceOperation(new ModelNode().setEmptyList());
        op.get(ClientConstants.INCLUDE_RUNTIME).set(true);
        final ModelNode result = client.execute(op);
        if (Operations.isSuccessfulOutcome(result)) {
            final ModelNode model = Operations.readResult(result);
            final String productName = getValue(model, "product-name", "WildFly");
            final String productVersion = getValue(model, "product-version");
            final String releaseVersion = getValue(model, "release-version");
            final String launchType = getValue(model, "launch-type");
            return new DefaultContainerDescription(productName, productVersion, releaseVersion, launchType,
                    "DOMAIN".equalsIgnoreCase(launchType));
        }
        throw new OperationExecutionException(op, result);
    }

    private static String getValue(final ModelNode model, final String attributeName) {
        return getValue(model, attributeName, null);
    }

    private static String getValue(final ModelNode model, final String attributeName, final String defaultValue) {
        if (model.hasDefined(attributeName)) {
            return model.get(attributeName).asString();
        }
        return defaultValue;
    }
}
