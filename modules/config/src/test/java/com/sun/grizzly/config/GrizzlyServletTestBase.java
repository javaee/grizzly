package com.sun.grizzly.config;

import javax.servlet.Servlet;

import com.sun.grizzly.http.servlet.ServletAdapter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import org.jvnet.hk2.config.Dom;

public abstract class GrizzlyServletTestBase {
    private GrizzlyConfig grizzlyConfig;

    @BeforeMethod(alwaysRun = true)
    public void setupServlet() {
        try {
            shutdownContainer();
            grizzlyConfig = new GrizzlyConfig(getConfigFile());
            grizzlyConfig.setupNetwork();
            int count = 0;
            for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
                addServlet(listener);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    protected String getConfigFile() {
        return "grizzly-config.xml";
    }

    @AfterMethod(alwaysRun = true)
    public void shutdownContainer() {
        if(grizzlyConfig != null) {
            grizzlyConfig.shutdown();
        }
    }
    
    private void addServlet(final GrizzlyServiceListener listener) {
        ServletAdapter sa = new ServletAdapter();
        sa.setServletInstance(getServlet());
        sa.setContextPath(getContextPath());
        sa.setServletPath(getServletPath());
        listener.getEmbeddedHttp().setAdapter(sa);
    }

    protected String getContextPath() {
        return "/grizzly-servlet-test";
    }

    protected String getServletPath() {
        return "/" + Dom.convertName(getClass().getSimpleName());
    }

    protected abstract Servlet getServlet();
}
