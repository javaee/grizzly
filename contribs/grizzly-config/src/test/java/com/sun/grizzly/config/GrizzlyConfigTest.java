package com.sun.grizzly.config;

import org.testng.annotations.Test;

/**
 * Created Jan 5, 2009
 *
 * @author <a href="mailto:jlee@antwerkz.com">Justin Lee</a>
 */
@Test
public class GrizzlyConfigTest {
    public void processConfig() throws Exception {
        final GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
        grizzlyConfig.setupNetwork();
    }
}
