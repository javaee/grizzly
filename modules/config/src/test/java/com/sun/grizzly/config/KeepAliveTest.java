package com.sun.grizzly.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.File;
import java.io.FileWriter;
import java.net.Socket;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.http.servlet.ServletAdapter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.jvnet.hk2.config.Dom;

@Test
public class KeepAliveTest extends BaseGrizzlyConfigTest {
    private GrizzlyConfig grizzlyConfig;
    private static final String GET_HTTP = "GET /index.html HTTP/1.1\n";
    private static final String KEEP_ALIVE_END = "KeepAlive:end";
    private static final String KEEP_ALIVE_PASS = "KeepAlive:PASS";

    @BeforeClass
    public void setup() {
        grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
        grizzlyConfig.setupNetwork();
        final GrizzlyServiceListener listener = grizzlyConfig.getListeners().get(0);
        listener.getEmbeddedHttp().setAdapter(new ServletAdapter(new KeepAliveServlet()));
    }

    @AfterClass
    public void tearDown() {
        grizzlyConfig.shutdown();
    }

    public void keepAlive() throws Exception {
        int count = 0;
        Socket s = new Socket("localhost", 38082);
        OutputStream os = s.getOutputStream();
        InputStream is = s.getInputStream();
        BufferedReader bis = new BufferedReader(new InputStreamReader(is));
        String line = null;
        sendGet(os);
        int tripCount = 0;
        try {
            while ((line = read(bis)) != null) {
                String[] strings = line.split(":");
                if (line.contains(KEEP_ALIVE_END)) {
                    count++;
                }
                if (line.contains(KEEP_ALIVE_END) && tripCount == 0) {
                    tripCount++;
                    System.out.println("*****  Getting again");
                    sendGet(os);
                }
            }
            Assert.assertEquals(tripCount, 1, "Should have tried to GET again");
            Assert.assertEquals(count, 2, "Should have gotten the content twice");
        } catch (Exception e) {
            Assert.fail(e.getMessage(), e);
        } finally {
            s.close();
            bis.close();
        }
    }

    private void sendGet(final OutputStream os) throws IOException {
        send(os, GET_HTTP);
        send(os, "Host: localhost:38082\n");
        send(os, "Connection: keep-alive\n");
        send(os, "\n");
    }

    private String read(final BufferedReader bis) throws IOException {
        String line = bis.readLine();
        System.out.println("from server: " + line);
        return line;
    }

    private void send(final OutputStream os, final String text) throws IOException {
        System.out.print("sending: " + text);
        os.write(text.getBytes());
    }

    @Override
    protected void setRootFolder(final GrizzlyServiceListener listener, final int count) {
        final StaticResourcesAdapter adapter = (StaticResourcesAdapter) listener.getEmbeddedHttp().getAdapter();
        final String name = System.getProperty("java.io.tmpdir", "/tmp") + "/"
            + Dom.convertName(getClass().getSimpleName()) + count;
        File dir = new File(name);
        dir.mkdirs();
        FileWriter writer;
        try {
            File file = new File(dir, "index.html");
            file.deleteOnExit();
            writer = new FileWriter(file);
            try {
                writer.write("<http><body>\n" + KEEP_ALIVE_PASS + "\n" + KEEP_ALIVE_END + "\n</body></html>\n");
                writer.flush();
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        } catch (IOException e) {
            Assert.fail(e.getMessage(), e);
        }
        adapter.addRootFolder(name);
    }

    private class KeepAliveServlet extends HttpServlet {
        @Override
        protected void doGet(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse)
            throws ServletException, IOException {
            httpServletResponse.getWriter().println(KEEP_ALIVE_PASS + "\n" + KEEP_ALIVE_END);
        }
    }
}