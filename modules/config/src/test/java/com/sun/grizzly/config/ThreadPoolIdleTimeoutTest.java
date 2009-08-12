package com.sun.grizzly.config;

import java.io.IOException;
import java.net.URL;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.testng.annotations.Test;

@Test
public class ThreadPoolIdleTimeoutTest extends GrizzlyServletTestBase {
    private String host = "localhost";
    private String contextPath = getContextPath();
    private String servletPath = getServletPath();

    @Override
    protected String getConfigFile() {
        return "grizzly-config-timeout-disabled.xml";
    }

    protected Servlet getServlet() {
        return new HttpServlet() {
            @Override
            protected void service(final HttpServletRequest req, final HttpServletResponse resp)
                throws ServletException, IOException {
                doGet(req, resp);
            }

            @Override
            protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
                throws ServletException, IOException {
                doGet(req, resp);
            }

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
    public void noTimeout() throws IOException {
        System.out.println("ThreadPoolIdleTimeoutTest.noTimeout");
        timeoutCheck(38084);
    }

    @Test(expectedExceptions = {IOException.class})
    public void timeout() throws IOException {
        System.out.println("ThreadPoolIdleTimeoutTest.timeout");
        timeoutCheck(38085);
    }

    private void timeoutCheck(final int port) throws IOException {
        new URL("http://" + host + ":" + port + contextPath + servletPath).getContent();
    }

}