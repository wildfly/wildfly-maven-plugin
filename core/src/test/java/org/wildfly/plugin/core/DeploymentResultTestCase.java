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

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DeploymentResultTestCase {

    @Test
    public void testSuccessful() {
        Assert.assertTrue(DeploymentResult.SUCCESSFUL.successful());
        Assert.assertNull(DeploymentResult.SUCCESSFUL.getFailureMessage());
        Assert.assertFalse(DeploymentResult.SUCCESSFUL.asModelNode().isDefined());

        final DeploymentResult deploymentResult = new DeploymentResult(createCompositeOutcome());
        Assert.assertTrue(deploymentResult.successful());
        Assert.assertNull(deploymentResult.getFailureMessage());
        Assert.assertTrue(deploymentResult.asModelNode().isDefined());
    }

    @Test
    public void testFailed() {
        // Create a failure description
        final ModelNode failureModel = createCompositeOutcome(
                "WFLYCTL0212: Duplicate resource [(\"deployment\" => \"foo.war\")]");

        DeploymentResult deploymentResult = new DeploymentResult(failureModel);
        Assert.assertFalse(deploymentResult.successful());
        Assert.assertEquals("WFLYCTL0212: Duplicate resource [(\"deployment\" => \"foo.war\")]",
                deploymentResult.getFailureMessage());
        Assert.assertTrue(deploymentResult.asModelNode().isDefined());

        // Create a failure not based on model node
        deploymentResult = new DeploymentResult("Test failed message");
        Assert.assertFalse(deploymentResult.successful());
        Assert.assertEquals("Test failed message", deploymentResult.getFailureMessage());
        Assert.assertFalse(deploymentResult.asModelNode().isDefined());

        // Create a failure not based on model node
        deploymentResult = new DeploymentResult("Test failed message %d", 2);
        Assert.assertFalse(deploymentResult.successful());
        Assert.assertEquals("Test failed message 2", deploymentResult.getFailureMessage());
        Assert.assertFalse(deploymentResult.asModelNode().isDefined());
    }

    @Test
    public void testFailureAssertion() {
        try {
            new DeploymentResult("Test failure result").assertSuccess();
            Assert.fail("Expected the deployment result to be a failure result");
        } catch (DeploymentException ignore) {
        }
        try {
            new DeploymentResult("Test failure result %d", 2).assertSuccess();
            Assert.fail("Expected the deployment result to be a failure result");
        } catch (DeploymentException ignore) {
        }

        // Create a failure description
        try {
            new DeploymentResult(createCompositeOutcome("WFLYCTL0212: Duplicate resource [(\"deployment\" => \"foo.war\")]"))
                    .assertSuccess();
            Assert.fail("Expected the deployment result to be a failure result");
        } catch (DeploymentException ignore) {
        }
    }

    private static ModelNode createCompositeOutcome() {
        return createCompositeOutcome(null);
    }

    private static ModelNode createCompositeOutcome(final String failureMessage) {
        final ModelNode response = createOutcome(failureMessage);
        final ModelNode result = response.get("result");
        result.get("step-1").set(createOutcome(failureMessage));
        return response;
    }

    private static ModelNode createOutcome(final String failureMessage) {
        final ModelNode result = new ModelNode().setEmptyObject();
        if (failureMessage == null) {
            result.get("outcome").set("success");
        } else {
            result.get("outcome").set("failed");
            result.get("failure-description").set(failureMessage);
            result.get("rolled-back").set(true);
        }
        return result;
    }
}
