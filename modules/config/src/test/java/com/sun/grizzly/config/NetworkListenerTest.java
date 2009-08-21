package com.sun.grizzly.config;

import java.io.IOException;
import java.io.File;
import java.net.URL;

import org.testng.Assert;
import org.testng.annotations.Test;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.Ssl;

@Test
public class NetworkListenerTest extends BaseGrizzlyConfigTest {
    public void secureListener() throws IOException, InstantiationException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            grizzlyConfig = new GrizzlyConfig("secure-listener.xml");
            grizzlyConfig.setupNetwork();
            final GrizzlyServiceListener service = grizzlyConfig.getListeners().get(0);
            final NetworkListener listener = grizzlyConfig.getConfig().getNetworkListeners().getNetworkListener()
                .get(0);
            final Ssl ssl = listener.findProtocol().getSsl();
            Assert.assertTrue(new File(ssl.getKeyStore()).exists(), "Should have a keystore file");
            Assert.assertTrue(new File(ssl.getTrustStore()).exists(), "Should have a truststore file");
            setRootFolder(service, 0);
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