package org.jboss.as.plugin.deployment;

import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;

import java.io.IOException;

/**
 * Redeploys the archived result of the project to the application server.
 * <p>
 * Example Usage: {@literal
 * <build>
 *      <plugins>
 *          ...
 *          <plugin>
 *              <groupId>org.jboss.as.plugins</groupId>
 *              <artifactId>jboss-as-maven-plugin</artifactId>
 *              <version>${jboss-as-maven-plugin-version}</version>
 *              <executions>
 *                  <execution>
 *                      <phase>package</phase>
 *                      <goals>
 *                          <goal>deploy-or-redeploy</goal>
 *                      </goals>
 *                  </execution>
 *              </executions>
 *          </plugin>
 *          ...
 *      </plugins>
 * </build>
 * }
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @goal deploy-or-redeploy
 */
public class DeployOrRedeploy extends AbstractDeployment {

    @Override
    public DeploymentPlan createPlan(final DeploymentPlanBuilder builder) throws IOException {
        DeploymentPlan plan = null;
        if (deploymentExists()) {
            getLog().debug("Deployment already exists, redeploying.");
            if (name() == null) {
                getLog().debug(nameNotDefinedMessage());
                plan = builder.replace(file()).redeploy(filename()).build();
            } else {
                getLog().debug(nameDefinedMessage());
                plan = builder.replace(name(), file()).redeploy(name()).build();
            }
        } else {
            getLog().debug("Deployment does not exist, deploying.");
            if (name() == null) {
                getLog().debug(nameNotDefinedMessage());
                plan = builder.add(file()).deploy(filename()).build();
            } else {
                getLog().debug(nameDefinedMessage());
                plan = builder.add(name(), file()).deploy(name()).build();
            }
        }
        return plan;
    }

    @Override
    public String goal() {
        return "deploy-or-redeploy";
    }
}
