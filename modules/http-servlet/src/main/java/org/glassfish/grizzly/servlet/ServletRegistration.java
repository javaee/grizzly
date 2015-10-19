/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly.servlet;


import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletSecurityElement;
import org.glassfish.grizzly.utils.ArraySet;

/**
 * Allows customization of a {@link javax.servlet.Servlet} registered with the {@link WebappContext}.
 *
 * @since 2.2
 */
public class ServletRegistration extends Registration
        implements javax.servlet.ServletRegistration.Dynamic, Comparable<ServletRegistration> {

    protected Class<? extends Servlet> servletClass;
    protected final ArraySet<String> urlPatterns = new ArraySet<String>(String.class);
    protected Servlet servlet;
    protected int loadOnStartup = -1;
    protected ExpectationHandler expectationHandler;
    protected boolean isAsyncSupported;

    /**
     * The run-as identity for this servlet.
     */
    private String runAs = null;

    // ------------------------------------------------------------ Constructors


    /**
     * Creates a new ServletRegistration associated with the specified
     * {@link WebappContext}.
     *
     * @param ctx the owning {@link WebappContext}.
     * @param name the name of the Filter.
     * @param servletClassName the fully qualified class name of the {@link Servlet}
     *  implementation.
     */
    protected ServletRegistration(final WebappContext ctx,
                                  final String name,
                                  final String servletClassName) {

        super(ctx, name, servletClassName);
        this.name = name;

    }

    /**
     * Creates a new ServletRegistration associated with the specified
     * {@link WebappContext}.
     *
     * @param ctx the owning {@link WebappContext}.
     * @param name the name of the Filter.
     * @param servlet the {@link Servlet} instance.
     */
    protected ServletRegistration(final WebappContext ctx,
                                  final String name,
                                  final Servlet servlet) {

        this(ctx, name, servlet.getClass());
        this.servlet = servlet;

    }

    /**
     * Creates a new ServletRegistration associated with the specified
     * {@link WebappContext}.
     *
     * @param ctx the owning {@link WebappContext}.
     * @param name the name of the Filter.
     * @param servletClass the class of the {@link Servlet} implementation.
     */
    protected ServletRegistration(final WebappContext ctx,
                                  final String name,
                                  final Class<? extends Servlet> servletClass) {

        this(ctx, name, servletClass.getName());
        this.servletClass = servletClass;

    }


    // ---------------------------------------------------------- Public Methods


    /**
     * Adds a servlet mapping with the given URL patterns for the Servlet
     * represented by this ServletRegistration.
     *
     * <p>If any of the specified URL patterns are already mapped to a
     * different Servlet, no updates will be performed.
     *
     * <p>If this method is called multiple times, each successive call
     * adds to the effects of the former.
     *
     * @param urlPatterns the URL patterns of the servlet mapping
     *
     * @return the (possibly empty) Set of URL patterns that are already
     * mapped to a different Servlet
     *
     * @throws IllegalArgumentException if <tt>urlPatterns</tt> is null
     * or empty
     * @throws IllegalStateException if the ServletContext from which this
     * ServletRegistration was obtained has already been initialized
     */
    @Override
    public Set<String> addMapping(String... urlPatterns) {
        if (ctx.deployed) {
            throw new IllegalStateException("WebappContext has already been deployed");
        }
        if (urlPatterns == null || urlPatterns.length == 0) {
            throw new IllegalArgumentException("'urlPatterns' cannot be null or zero-length");
        }
        this.urlPatterns.addAll(urlPatterns);
        return Collections.emptySet(); // TODO - need to comply with the spec at some point
    }

    /**
     * Gets the currently available mappings of the
     * Servlet represented by this <code>ServletRegistration</code>.
     *
     * <p>If permitted, any changes to the returned <code>Collection</code> must not
     * affect this <code>ServletRegistration</code>.
     *
     * @return a (possibly empty) <code>Collection</code> of the currently
     * available mappings of the Servlet represented by this
     * <code>ServletRegistration</code>
     */
    @Override
    public Collection<String> getMappings() {
        return Collections.unmodifiableList(
                Arrays.asList(urlPatterns.getArrayCopy()));
    }

    /**
     * Sets the <code>loadOnStartup</code> priority on the Servlet
     * represented by this dynamic ServletRegistration.
     * <p/>
     * <p>A <tt>loadOnStartup</tt> value of greater than or equal to
     * zero indicates to the container the initialization priority of
     * the Servlet. In this case, the container must instantiate and
     * initialize the Servlet during the initialization phase of the
     * WebappContext, that is, after it has invoked all of the
     * ServletContextListener objects configured for the WebappContext
     * at their {@link javax.servlet.ServletContextListener#contextInitialized}
     * method.
     * <p/>
     * <p>If <tt>loadOnStartup</tt> is a negative integer, the container
     * is free to instantiate and initialize the Servlet lazily.
     * <p/>
     * <p>The default value for <tt>loadOnStartup</tt> is <code>-1</code>.
     * <p/>
     * <p>A call to this method overrides any previous setting.
     *
     * @param loadOnStartup the initialization priority of the Servlet
     * @throws IllegalStateException if the ServletContext from which
     *                               this ServletRegistration was obtained has already been initialized
     */
    @Override
    public void setLoadOnStartup(int loadOnStartup) {
        if (ctx.deployed) {
            throw new IllegalStateException("WebappContext has already been deployed");
        }
        if (loadOnStartup < 0) {
            this.loadOnStartup = -1;
        } else {
            this.loadOnStartup = loadOnStartup;
        }
    }

    /**
     * Get the {@link ExpectationHandler} responsible for processing
     * <tt>Expect:</tt> header (for example "Expect: 100-Continue").
     * 
     * @return the {@link ExpectationHandler} responsible for processing
     * <tt>Expect:</tt> header (for example "Expect: 100-Continue").
     */
    public ExpectationHandler getExpectationHandler() {
        return expectationHandler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> setServletSecurity(ServletSecurityElement constraint) {
        return Collections.emptySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMultipartConfig(MultipartConfigElement multipartConfig) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRunAsRole() {
        return runAs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRunAsRole(String roleName) {
        this.runAs = roleName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAsyncSupported(boolean isAsyncSupported) {
        this.isAsyncSupported = isAsyncSupported;
    }
    
    /**
     * Set the {@link ExpectationHandler} responsible for processing
     * <tt>Expect:</tt> header (for example "Expect: 100-Continue").
     * 
     * @param expectationHandler  the {@link ExpectationHandler} responsible
     * for processing <tt>Expect:</tt> header (for example "Expect: 100-Continue").
     */
    public void setExpectationHandler(ExpectationHandler expectationHandler) {
        this.expectationHandler = expectationHandler;
    }
    
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ServletRegistration");
        sb.append("{ servletName=").append(name);
        sb.append(", servletClass=").append(className);
        sb.append(", urlPatterns=").append(Arrays.toString(urlPatterns.getArray()));
        sb.append(", loadOnStartup=").append(loadOnStartup);
        sb.append(", isAsyncSupported=").append(isAsyncSupported);
        sb.append(" }");
        return sb.toString();
    }


    // ------------------------------------------------- Methods from Comparable


    @Override
    public int compareTo(ServletRegistration o) {
        if (loadOnStartup == o.loadOnStartup) {
            return 0;
        }
        if (loadOnStartup < 0 && o.loadOnStartup < 0) {
            return -1;
        }
        if (loadOnStartup >= 0 && o.loadOnStartup >= 0) {
            if (loadOnStartup < o.loadOnStartup) {
                return -1;
            }
        }
        return 1;
    }
}
