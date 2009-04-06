package com.sun.grizzly.config;

import com.sun.grizzly.config.dom.ThreadPool;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
            int count = 0;
            for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
                setRootFolder(listener, count++);
            }
            final String content = getContent(new URL("http://localhost:8080").openConnection());
            final String content2 = getContent(new URL("http://localhost:8181").openConnection());
            Assert.assertEquals(content, "<html><body>You've found the server on port 8080</body></html>");
            Assert.assertEquals(content2, "<html><body>You've found the server on port 8181</body></html>");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    private String getContent(URLConnection connection) throws IOException {
        final InputStream inputStream = connection.getInputStream();
        InputStreamReader reader = new InputStreamReader(inputStream);
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[1024];
        int read;
        while(( read = reader.read(buffer)) != -1) {
            builder.append(buffer, 0, read);
        }

        return builder.toString();
    }

    private void setRootFolder(GrizzlyServiceListener listener, int count) throws IOException {
        final GrizzlyEmbeddedHttp http = listener.getEmbeddedHttp();
        final StaticResourcesAdapter adapter = (StaticResourcesAdapter) http.getAdapter();
        final String name = System.getProperty("java.io.tmpdir", "/tmp") + "/grizzly-config-root" + count;
        File dir = new File(name);
        dir.mkdirs();
        final FileWriter writer = new FileWriter(new File(dir, "index.html"));
        writer.write("<html><body>You've found the server on port " + http.getPort() + "</body></html>");
        writer.flush();
        writer.close();
        adapter.setRootFolder(name);
    }

    public void defaults() {
        final GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
        final ThreadPool threadPool = grizzlyConfig.getConfig().getNetworkListeners().getThreadPool().get(0);
        Assert.assertEquals(threadPool.getMaxThreadPoolSize(), "200"); 
    }
}
