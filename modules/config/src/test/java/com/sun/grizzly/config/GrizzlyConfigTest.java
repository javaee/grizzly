package com.sun.grizzly.config;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.ThreadPool;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Created Jan 5, 2009
 *
 * @author <a href="mailto:justin.lee@sun.com">Justin Lee</a>
 */
@SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
@Test
public class GrizzlyConfigTest extends BaseGrizzlyConfigTest {
    public void processConfig() throws IOException, InstantiationException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
            grizzlyConfig.setupNetwork();
            int count = 0;
            for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
                setRootFolder(listener, count++);
            }
            final String content = getContent(new URL("http://localhost:38082").openConnection());
            final String content2 = getContent(new URL("http://localhost:38083").openConnection());
            Assert.assertEquals(content, "<html><body>You've found the server on port 38082</body></html>");
            Assert.assertEquals(content2, "<html><body>You've found the server on port 38083</body></html>");
        } finally {
            grizzlyConfig.shutdown();
        }
    }

    public void references() throws IOException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
            final List<NetworkListener> list = grizzlyConfig.getConfig().getNetworkListeners().getNetworkListener();
            final NetworkListener listener = list.get(0);
            boolean found = false;
            for (NetworkListener ref : listener.findProtocol().findNetworkListeners()) {
                found |= ref.getName().equals(listener.getName());
            }
            Assert.assertTrue(found, "Should find the NetworkListener in the list of references from Protocol");
            found = false;
            for (NetworkListener ref : listener.findTransport().findNetworkListeners()) {
                found |= ref.getName().equals(listener.getName());
            }
            Assert.assertTrue(found, "Should find the NetworkListener in the list of references from Transport");
            found = false;
            for (NetworkListener ref : listener.findThreadPool().findNetworkListeners()) {
                found |= ref.getName().equals(listener.getName());
            }
            Assert.assertTrue(found, "Should find the NetworkListener in the list of references from ThreadPool");
        } finally {
            grizzlyConfig.shutdown();
        }
    }

    public void defaults() throws IOException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
            final ThreadPool threadPool = grizzlyConfig.getConfig().getNetworkListeners().getThreadPool().get(0);
            Assert.assertEquals(threadPool.getMaxThreadPoolSize(), "5");
        } finally {
            grizzlyConfig.shutdown();
        }
    }

    @Test(enabled = false)
    public void ssl() throws URISyntaxException, IOException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            configure();
            grizzlyConfig = new GrizzlyConfig("grizzly-config-ssl.xml");
            grizzlyConfig.setupNetwork();
            int count = 0;
            for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
                setRootFolder(listener, count++);
            }
//            Assert.assertEquals(getContent(new URL("https://localhost:38082").openConnection()),
//                "<html><body>You've found the server on port 38082</body></html>");
/*
            try {
                System.out.println("******************** sleeping");
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e.getMessage());
            }
*/
            Assert.assertEquals(getContent(new URL("https://localhost:38083").openConnection()),
                "<html><body>You've found the server on port 38083</body></html>");
        } finally {
            grizzlyConfig.shutdown();
        }
    }

    private void configure() throws URISyntaxException {
        ClassLoader cl = getClass().getClassLoader();
        System.setProperty("javax.net.ssl.trustStore", new File(cl.getResource("cacerts.jks").toURI()).getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.keyStore", new File(cl.getResource("keystore.jks").toURI()).getAbsolutePath());
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
    }

    @Test(expectedExceptions = {GrizzlyConfigException.class}, enabled = false)
    public void badConfig() throws IOException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config-bad.xml");
            grizzlyConfig.setupNetwork();
        } finally {
            grizzlyConfig.shutdown();
        }
    }

    public void timeoutDisabled() throws IOException, InstantiationException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config-timeout-disabled.xml");
            grizzlyConfig.setupNetwork();
        } finally {
            grizzlyConfig.shutdown();
        }
    }
}