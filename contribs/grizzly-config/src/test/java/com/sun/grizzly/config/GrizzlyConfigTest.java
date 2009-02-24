package com.sun.grizzly.config;

import org.testng.annotations.Test;
import org.testng.Assert;

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
        final GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
        grizzlyConfig.setupNetwork();
        final URLConnection urlConnection = new URL("http://localhost:8080").openConnection();
        Assert.assertNotNull(urlConnection);
    }
}
