package com.sun.grizzly.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.File;
import java.io.FileWriter;
import java.net.Socket;

import com.sun.grizzly.tcp.StaticResourcesAdapter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.jvnet.hk2.config.Dom;

@Test
public class KeepAliveTest extends BaseGrizzlyConfigTest {
    private GrizzlyConfig grizzlyConfig;
    private static final String GET_HTTP = "GET /index.html HTTP/1.0\n";
    private static final String KEEP_ALIVE_END = "KeepAlive:end";
    private static final String KEEP_ALIVE_PASS = "KeepAlive:PASS";
    private static final Integer PORT_ONE = 38082;
    private static final Integer PORT_TWO = PORT_ONE + 1;
    private static final String HOST = "localhost";
    private boolean debug = true;

    @BeforeClass
    public void setup() {
        grizzlyConfig = new GrizzlyConfig("keep-alive.xml");
        grizzlyConfig.setupNetwork();
        int count = 0;
        for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
            setRootFolder(listener, count++);
        }
    }

    @AfterClass
    public void tearDown() {
        grizzlyConfig.shutdown();
    }

    @SuppressWarnings({"SocketOpenedButNotSafelyClosed"})
    public void keepAlive() throws Exception {
        int count = 0;
        Socket s = new Socket(HOST, PORT_ONE);
        OutputStream os = s.getOutputStream();
        InputStream is = s.getInputStream();
        String line;
        sendGet(os);
        int tripCount = 0;
        BufferedReader bis = new BufferedReader(new InputStreamReader(is));
        try {
            while ((line = read(bis)) != null) {
                if (line.contains(KEEP_ALIVE_END)) {
                    count++;
                    if (tripCount == 0) {
                        tripCount++;
                        sendGet(os);
                    }
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

    @SuppressWarnings({"SocketOpenedButNotSafelyClosed"})
    public void keepAliveTimeoutZero() throws Exception {
        boolean found = false;
        Socket sock = new Socket(HOST, PORT_TWO);
        sock.setSoTimeout(50000);
        OutputStream os = sock.getOutputStream();
        send(os, "GET /index.html HTTP/1.1\n");
        send(os, String.format("Host: localhost:%s\n", PORT_TWO));
        send(os, "\n");
        BufferedReader bis = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        try {
            while (read(bis) != null) {
                found = true;
            }
            Assert.assertTrue(found, "Should have gotten the document content");
            System.out.println("getting again");
            send(os, "GET /index.html HTTP/1.1\n");
            send(os, "\n");
            Assert.fail("Second GET should fail");
        } catch (Exception e) {
        } finally {
            sock.close();
            bis.close();
        }
    }

    private void sendGet(final OutputStream os) throws IOException {
        send(os, GET_HTTP);
        send(os, "Connection: keep-alive\n");
        send(os, "\n");
    }

    private String read(final BufferedReader bis) throws IOException {
        String line = bis.readLine();
        System.out.println("from server: " + line);
        return line;
    }

    private void send(final OutputStream os, final String text) throws IOException {
        if (debug) {
            System.out.print("sending: " + text);
        }
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
}