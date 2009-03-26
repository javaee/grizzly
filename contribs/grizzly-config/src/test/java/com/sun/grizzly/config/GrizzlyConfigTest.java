package com.sun.grizzly.config;

import com.sun.grizzly.config.dom.ThreadPool;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Logger;

/**
 * Created Jan 5, 2009
 *
 * @author <a href="mailto:justin.lee@sun.com">Justin Lee</a>
 */
@Test
public class GrizzlyConfigTest {
    private static final Logger log = Logger.getLogger(GrizzlyConfigTest.class.getName());

    public void processConfig() throws IOException, InstantiationException {
        try {
            final GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
            grizzlyConfig.setupNetwork();
            final URLConnection urlConnection = new URL("http://localhost:8080").openConnection();
            Assert.assertNotNull(urlConnection);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    public void defaults() {
        final GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
        final ThreadPool threadPool = grizzlyConfig.getConfig().getNetworkListeners().getThreadPool().get(0);
        Assert.assertEquals(threadPool.getMaxThreadPoolSize(), "200"); 
    }
}
