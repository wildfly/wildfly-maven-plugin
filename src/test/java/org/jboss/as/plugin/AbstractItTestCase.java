package org.jboss.as.plugin;

import java.net.InetAddress;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

/**
 * Abstract Arquillian integration testcase.
 * 
 * @author <a href="mailto:heinz.wilming@akquinet.de">Heinz Wilming</a>
 * 
 */
@RunWith(Arquillian.class)
public abstract class AbstractItTestCase extends AbstractMojoTestCase {

    @Deployment(managed = false, testable = false)
    public static JavaArchive dummyDeployment() {
        return ShrinkWrap.create(JavaArchive.class);
    }

    @Before
    public final void setUpMojoTestCase() throws Exception {
        super.setUp();
    }

    @After
    public final void tearDownMojoTestCase() throws Exception {
        super.tearDown();
    }

    protected final ModelNode execute(final ModelNode operation) throws Exception {
        final ModelControllerClient client = createClient();

        try {
            return client.execute(operation);
        } finally {
            client.close();
        }
    }

    private ModelControllerClient createClient() throws Exception {
        // waiting, because server is maybe in reload state
        Thread.sleep(1000);
        return ModelControllerClient.Factory.create(InetAddress.getByName("localhost"), 10099);
    }

}
