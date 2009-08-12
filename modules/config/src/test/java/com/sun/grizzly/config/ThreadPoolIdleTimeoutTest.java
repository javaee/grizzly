package com.sun.grizzly.config;

import java.io.IOException;
import java.net.URL;
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
            public void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
                try {
                    Thread.sleep(10000);
                    response.setContentType("text/plain");
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
        try {
            URL url = new URL("http://" + host + ":" + port + contextPath + servletPath);
            url.getContent();
            Assert.assertTrue(noTimeout, "Servlet should not timeout");
        } catch (IOException ex) {
            ex.printStackTrace();
            Assert.fail("Servlet should respond", ex);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("Servlet should respond", ex);
        }
    }

}