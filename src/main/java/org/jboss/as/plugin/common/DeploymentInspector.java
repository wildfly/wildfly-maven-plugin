/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.jboss.as.plugin.common;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;

/**
 * Utility to lookup up Deployments.
 *
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
public class DeploymentInspector {

    /**
     * Utility Constructor.
     */
    private DeploymentInspector() {

    }

    /**
     * Lookup an existing Deployment using a static name or a pattern. At least exactComparisonName or deploymentNamePattern
     * must be set.
     *
     * @param client
     * @param exactComparisonName Name for exact matching.
     * @param matchPattern Regex-Pattern for deployment matching.
     * @return the name of the deployment or null.
     */
    public static List<String> getDeployments(ModelControllerClient client, String exactComparisonName, String matchPattern) {

        if (exactComparisonName == null && matchPattern == null) {
            throw new IllegalArgumentException("exactComparisonName and matchPattern are null. One of them must "
                    + "be set in order to find an existing deployment.");
        }

        // CLI :read-children-names(child-type=deployment)
        final ModelNode op = ServerOperations.createListDeploymentsOperation();
        final ModelNode listDeploymentsResult;
        final List<String> result = new ArrayList<String>();
        try {
            listDeploymentsResult = client.execute(op);
            // Check to make sure there is an outcome
            if (Operations.isSuccessfulOutcome(listDeploymentsResult)) {
                if (ServerOperations.isSuccessfulOutcome(listDeploymentsResult)) {
                    final List<ModelNode> deployments = ServerOperations.readResult(listDeploymentsResult).asList();
                    for (ModelNode n : deployments) {

                        if (matches(n.asString(), exactComparisonName, matchPattern)) {
                            result.add(n.asString());
                        }
                    }
                }
            } else {
                throw new IllegalStateException(ServerOperations.getFailureDescriptionAsString(listDeploymentsResult));
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Could not execute operation '%s'", op), e);
        }

        Collections.sort(result);
        return result;

    }

    private static boolean matches(String deploymentName, String exactComparisonName, String matchPattern) {

        if (matchPattern != null) {
            return deploymentName.matches(matchPattern);
        }

        return exactComparisonName.equals(deploymentName);
    }
}
