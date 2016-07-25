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

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.PATH;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;

/**
 * Allows content to be added to an operation. The content will be attached to an {@link OperationBuilder} with either
 * the {@link OperationBuilder#addInputStream(InputStream)} or {@link OperationBuilder#addFileAsAttachment(File)}
 * depending on the type of content being used.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class DeploymentContent {

    /**
     * Adds the content to the operation.
     *
     * @param builder the builder used to attach the content to
     * @param op      the deployment operation to be modified with the information required to represent the content
     *                being deployed
     */
    abstract void addContentToOperation(OperationBuilder builder, ModelNode op);

    /**
     * If a name can be resolved from the content that name will be used, otherwise {@code null} will be returned.
     *
     * @return the name resolved from the content or {@code null} if no name could be resolved
     */
    String resolvedName() {
        return null;
    }

    /**
     * Creates new deployment content based on a file system path.
     * <p>
     * The {@link #resolvedName()} will return the name of the file.
     * </p>
     *
     * @param content the path to the content
     *
     * @return the deployment content
     */
    static DeploymentContent of(final Path content) {
        return new DeploymentContent() {

            @Override
            void addContentToOperation(final OperationBuilder builder, final ModelNode op) {
                final ModelNode contentNode = op.get(CONTENT);
                final ModelNode contentItem = contentNode.get(0);
                // If the content points to a directory we are deploying exploded content
                if (Files.isDirectory(content)) {
                    contentItem.get(PATH).set(content.toAbsolutePath().toString());
                    contentItem.get("archive").set(false);
                } else {
                    // The index is 0 based so use the input stream count before adding the input stream
                    contentItem.get(ClientConstants.INPUT_STREAM_INDEX).set(builder.getInputStreamCount());
                    builder.addFileAsAttachment(content.toFile());
                }
            }

            @Override
            String resolvedName() {
                return content.getFileName().toString();
            }

            @Override
            public String toString() {
                return String.format("%s(%s)", DeploymentContent.class.getName(), content);
            }
        };
    }

    /**
     * Creates new deployment content based on the stream content. The stream content is copied, stored in-memory and
     * closed.
     *
     * @param content the content to deploy
     *
     * @return the deployment content
     */
    static DeploymentContent of(final InputStream content) {
        final ByteArrayInputStream copiedContent = copy(content);
        return new DeploymentContent() {
            @Override
            void addContentToOperation(final OperationBuilder builder, final ModelNode op) {
                copiedContent.reset();
                final ModelNode contentNode = op.get(CONTENT);
                final ModelNode contentItem = contentNode.get(0);
                // The index is 0 based so use the input stream count before adding the input stream
                contentItem.get(ClientConstants.INPUT_STREAM_INDEX).set(builder.getInputStreamCount());
                builder.addInputStream(copiedContent);
            }

            @Override
            public String toString() {
                return String.format("%s(%s)", DeploymentContent.class.getName(), copiedContent);
            }
        };
    }

    private static ByteArrayInputStream copy(final InputStream in) {
        final ByteArrayOutputStream copy = new ByteArrayOutputStream();
        final byte[] buffer = new byte[64];
        int len;
        try {
            while ((len = in.read(buffer)) > 0) {
                copy.write(buffer, 0, len);
            }
            return new ByteArrayInputStream(copy.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy input stream.", e);
        } finally {
            try {
                in.close();
            } catch (IOException ignore) {
            }
        }
    }
}
