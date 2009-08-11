package com.sun.grizzly.config;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.Socket;
import java.util.Date;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.testng.annotations.Test;
import org.testng.Assert;

@Test
public class ThreadPoolIdleTimeoutTest extends GrizzlyServletTestBase {
    private String host = "localhost";
    private int port = 38084;
    private String contextPath = getContextPath();
    private String servletPath = getServletPath();
    private boolean noTimeout = true;

    @Override
    protected String getConfigFile() {
        return "grizzly-config-timeout-disabled.xml";
    }

    protected Servlet getServlet() {
        return new HttpServlet() {
            @Override
            protected void service(final HttpServletRequest request, final HttpServletResponse response)
                throws ServletException, IOException {
/*
                super.service(req, resp);
            }

            public void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
*/
                try {
                    Thread.sleep(10000);
                    response.getWriter().println("Here's your content.");
                    response.flushBuffer();
                } catch (InterruptedException ie) {
                    throw new ServletException(ie.getMessage(), ie);
                }
            }
        };
    }

    @Test
    public void noTimeout() {
//        goGet();
//        invokeServlet();
    }

    private void goGet() {
        try {
            URL url = new URL("http://" + host + ":" + port + contextPath + servletPath);
            System.out.println("getting content: " + (new Date()));
            url.getContent();
            Assert.assertTrue(noTimeout, "Servlet should not timeout");
        } catch (IOException ex) {
            ex.printStackTrace();
            Assert.fail("Servlet should respond", ex);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("Servlet should respond", ex);
        } finally {
            System.out.println("done getting content: " + (new Date()));
        }
    }

    private void invokeServlet() {
        try {
            sock = new Socket(host, port);
            try {
                OutputStream os = sock.getOutputStream();
                String get = "GET " + contextPath + servletPath + " HTTP/1.1\n";
                os.write(get.getBytes());
                String hostHeader = "Host: " + host + ":" + port + "\n";
                os.write(hostHeader.getBytes());
                os.write("\n".getBytes());
                InputStream is = sock.getInputStream();
                BufferedReader bis = new BufferedReader(new InputStreamReader(is));
                boolean responseOK = false;
                boolean contentLenFound = false;
                String line;
                while ((line = bis.readLine()) != null) {
                    System.out.println(line);
                    responseOK |= line.contains("HTTP/1.1 200 OK");
                    contentLenFound |= line.contains("Content-Length:") || line.contains("Content-length:");
                }
                if (responseOK && contentLenFound) {
                    Assert.assertTrue(true, "Response OK");
                } else if (!responseOK) {
                    Assert.fail("Wrong response code, expected 200 OK");
                } else {
                    Assert.fail("Missing Content-Length response header");
                }
            } finally {
                if (sock != null) {
                    sock.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage(), e);
        }
    }

    Socket sock = null;

}