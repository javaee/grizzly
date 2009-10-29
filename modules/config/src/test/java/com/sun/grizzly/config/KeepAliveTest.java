package com.sun.grizzly.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sun.grizzly.http.servlet.ServletAdapter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test(enabled = false)
public class KeepAliveTest extends BaseGrizzlyConfigTest {
    private GrizzlyConfig grizzlyConfig;
    private static final String GET_HTTP_1_0 = "GET / HTTP/1.0\n";

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

    public void dump() throws Exception {
        int count = 0;
        Socket s = new Socket("localhost", 38082);
        OutputStream os = s.getOutputStream();
        os.write(GET_HTTP_1_0.getBytes());
        os.write("Connection: keep-alive\n".getBytes());
        os.write("\n".getBytes());
        InputStream is = s.getInputStream();
        BufferedReader bis = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = bis.readLine()) != null) {
            System.out.println(line);
        }
    }

    public void goGet() throws Exception {
        int count = 0;
        Socket s = new Socket("localhost", 38082);
        OutputStream os = s.getOutputStream();
        os.write(GET_HTTP_1_0.getBytes());
        os.write("Connection: keep-alive\n".getBytes());
        os.write("\n".getBytes());
        InputStream is = s.getInputStream();
        BufferedReader bis = new BufferedReader(new InputStreamReader(is));
        String line = null;
        int tripCount = 0;
        try {
            while ((line = bis.readLine()) != null) {
                System.out.println(line);
                int index = line.indexOf("Connection:");
                if (index >= 0) {
                    index = line.indexOf(":");
                    String state = line.substring(index + 1).trim();
                    if ("keep-alive".equalsIgnoreCase(state)) {
//                        stat.addStatus("web-keepalive ", stat.PASS);
                        count++;
                    }
                }
                if (line.contains("KeepAlive:end")) {
                    if (++tripCount == 1) {
                        System.out.println("GET / HTTP/1.0");
                        os.write(GET_HTTP_1_0.getBytes());
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new Exception("Test UNPREDICTED-FAILURE");
        } finally {
            s.close();
            bis.close();
        }
    }

    private class KeepAliveServlet extends HttpServlet {
        @Override
        protected void doGet(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse)
            throws ServletException, IOException {
            httpServletResponse.getWriter().println("KeepAlive:PASS\nKeepAlive:end");
        }
    }
}