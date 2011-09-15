package org.glassfish.grizzly.servlet;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

public interface FilterChainInvoker {

    void invokeFilterChain(ServletRequest request, ServletResponse response)
            throws IOException, ServletException;

}
