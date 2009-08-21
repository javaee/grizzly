package com.sun.grizzly.config;

import java.io.IOException;
import java.net.URL;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class NetworkListenerTest extends BaseGrizzlyConfigTest {
    public void secureListener() throws IOException, InstantiationException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            grizzlyConfig = new GrizzlyConfig("secure-listener.xml");
            grizzlyConfig.setupNetwork();
            int count = 0;
            for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
                setRootFolder(listener, count++);
            }
//            Assert.assertEquals(getContent(new URL("https://localhost:38083").openConnection()),
//                "<html><body>You've found the server on port 38083</body></html>");
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage(), e);
        } finally {
            if (grizzlyConfig != null) {
                grizzlyConfig.shutdown();
            }
        }
    }
}