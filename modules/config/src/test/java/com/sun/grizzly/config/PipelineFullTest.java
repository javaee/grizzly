package com.sun.grizzly.config;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.SocketException;
import java.util.concurrent.RejectedExecutionException;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class PipelineFullTest extends BaseGrizzlyConfigTest {
    @Test(expectedExceptions = {SocketException.class})
    public void check() throws IOException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
            grizzlyConfig.getConfig().getNetworkListeners().getNetworkListener().get(0)
                .findThreadPool().setMaxQueueSize("0");
            grizzlyConfig.setupNetwork();
            int count = 0;
            for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
                setRootFolder(listener, count++);
            }

            
            HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:38082/index.html").openConnection();
            conn.setDoOutput(true);
            Assert.assertEquals(conn.getResponseCode(), 503, "Service should be unavailable");
        } finally {
            grizzlyConfig.shutdown();
        }
    }
}
