package com.sun.grizzly.config;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.nio.channels.UnresolvedAddressException;

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
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        } finally {
            if (grizzlyConfig != null) {
                grizzlyConfig.shutdown();
            }
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
            if (grizzlyConfig != null) {
                grizzlyConfig.shutdown();
            }
        }
    }

    public void defaults() throws IOException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
            final ThreadPool threadPool = grizzlyConfig.getConfig().getNetworkListeners().getThreadPool().get(0);
            Assert.assertEquals(threadPool.getMaxThreadPoolSize(), "5");
        } finally {
            if (grizzlyConfig != null) {
                grizzlyConfig.shutdown();
            }
        }
    }

    @Test(expectedExceptions = {GrizzlyConfigException.class})
    public void badConfig() throws IOException, InstantiationException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config-bad.xml");
            grizzlyConfig.setupNetwork();
        } finally {
            if (grizzlyConfig != null) {
                grizzlyConfig.shutdown();
            }
        }
    }

    public void timeoutDisabled() throws IOException, InstantiationException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config-timeout-disabled.xml");
            grizzlyConfig.setupNetwork();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        } finally {
            if (grizzlyConfig != null) {
                grizzlyConfig.shutdown();
            }
        }
    }
}