package com.sun.grizzly.config;

import java.util.logging.Logger;

import org.testng.annotations.Test;

/**
 * Created Jan 5, 2009
 *
 * @author <a href="mailto:jlee@antwerkz.com">Justin Lee</a>
 */
@Test
public class GrizzlyConfigTest {
    private static final Logger log = Logger.getLogger(GrizzlyConfigTest.class.getName());

    public void processConfig() {
        final GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
        grizzlyConfig.setupNetwork();
    }
}
