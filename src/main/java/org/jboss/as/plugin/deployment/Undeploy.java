/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.plugin.deployment;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.as.plugin.common.PropertyNames;
import org.jboss.as.plugin.deployment.Deployment.Type;

/**
 * Undeploys the application to the JBoss Application Server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Mojo(name = "undeploy", threadSafe = true)
public class Undeploy extends AbstractAppDeployment {

    /**
     * Specifies the name match pattern for undeploying/replacing artifacts.
     */
    @Parameter(alias = "match-pattern")
    protected String matchPattern;

    /**
     * Specifies the strategy in case more than one matching artifact is found.
     * <ul>
     *     <li>first: The first artifact is taken for undeployment/replacement. Other artifacts won't be touched.
     *     The list of artifacts is sorted using the default collator.</li>
     *     <li>all: All matching artifacts are undeployed.</li>
     *     <li>fail: Deployment fails.</li>
     * </ul>
     */
    @Parameter(alias = "match-pattern-strategy")
    protected String matchPatternStrategy = MatchPatternStrategy.FAIL.toString();

    /**
     * Indicates whether undeploy should ignore the undeploy operation if the deployment does not exist.
     */
    @Parameter(defaultValue = "true", property = PropertyNames.IGNORE_MISSING_DEPLOYMENT)
    private boolean ignoreMissingDeployment;

    @Override
    protected String getMatchPattern() {
        return matchPattern;
    }

    @Override
    protected MatchPatternStrategy getMatchPatternStrategy() {
        if (MatchPatternStrategy.FAIL.toString().equalsIgnoreCase(matchPatternStrategy)) {
            return MatchPatternStrategy.FAIL;
        } else if (MatchPatternStrategy.FIRST.toString().equalsIgnoreCase(matchPatternStrategy)) {
            return MatchPatternStrategy.FIRST;
        } else if (MatchPatternStrategy.ALL.toString().equalsIgnoreCase(matchPatternStrategy)) {
            return MatchPatternStrategy.ALL;
        }
        throw new IllegalStateException(
                String.format("matchPatternStrategy '%s' is not a valid strategy. Valid strategies are %s, %s and %s",
                        matchPatternStrategy, MatchPatternStrategy.ALL, MatchPatternStrategy.FAIL, MatchPatternStrategy.FIRST));
    }

    @Override
    public String goal() {
        return "undeploy";
    }

    @Override
    public Type getType() {
        return (ignoreMissingDeployment ? Type.UNDEPLOY_IGNORE_MISSING : Type.UNDEPLOY);
    }

}
