/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;

/**
 * Allows customization of a {@link Filter} registered with the {@link WebappContext}.
 *
 * @since 2.2
 */
public class FilterRegistration extends Registration implements javax.servlet.FilterRegistration.Dynamic {

    protected Class<? extends Filter> filterClass;
    protected Filter filter;
    
    protected boolean isAsyncSupported;

    // ------------------------------------------------------------ Constructors

    /**
     * Creates a new FilterRegistration associated with the specified
     * {@link WebappContext}.
     *
     * @param ctx the owning {@link WebappContext}.
     * @param name the name of the Filter.
     * @param filterClassName the fully qualified class name of the {@link Filter}
     *  implementation.
     */
    protected FilterRegistration(final WebappContext ctx,
                                 final String name,
                                 final String filterClassName) {

        super(ctx, name, filterClassName);
        initParameters = new HashMap<String, String>(4, 1.0f);

    }

    /**
     * Creates a new FilterRegistration associated with the specified
     * {@link WebappContext}.
     *
     * @param ctx the owning {@link WebappContext}.
     * @param name name the name of the Filter.
     * @param filter the class of the {@link Filter} implementation
     */
    protected FilterRegistration(final WebappContext ctx,
                                 final String name,
                                 final Class<? extends Filter> filter) {
        this(ctx, name, filter.getName());
        this.filterClass = filter;

    }

    /**
     * Creates a new FilterRegistration associated with the specified
     * {@link WebappContext}.
     *
     * @param ctx the owning {@link WebappContext}.
     * @param name name the name of the Filter.
     * @param filter the {@link Filter} instance.
     */
    protected FilterRegistration(final WebappContext ctx,
                                 final String name,
                                 final Filter filter) {
        this(ctx, name, filter.getClass());
        this.filter = filter;
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * Adds a filter mapping with the given servlet names and dispatcher
     * types for the Filter represented by this FilterRegistration.
     *
     * <p>Filter mappings are matched in the order in which they were
     * added.
     *
     * <p>If this method is called multiple times, each successive call
     * adds to the effects of the former.
     *
     * @param dispatcherTypes the dispatcher types of the filter mapping,
     * or null if the default <tt>DispatcherType.REQUEST</tt> is to be used
     * @param servletNames the servlet names of the filter mapping
     *
     * @throws IllegalArgumentException if <tt>servletNames</tt> is null or
     * empty
     * @throws IllegalStateException if the ServletContext from which this
     * FilterRegistration was obtained has already been initialized
     */
    public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes,
                                          String... servletNames) {
        addMappingForServletNames(dispatcherTypes, true, servletNames);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes,
            boolean isMatchAfter, String... servletNames) {
        if (ctx.deployed) {
            throw new IllegalStateException("WebappContext has already been deployed");
        }

        if ((servletNames==null) || (servletNames.length==0)) {
            throw new IllegalArgumentException("'servletNames' is null or zero-length");
        }

        for (String servletName : servletNames) {
            FilterMap fmap = new FilterMap();
            fmap.setFilterName(getName());
            fmap.setServletName(servletName);
            fmap.setDispatcherTypes(dispatcherTypes);

            ctx.addFilterMap(fmap, isMatchAfter);
        }
    }

    /**
     * Gets the currently available servlet name mappings
     * of the Filter represented by this <code>FilterRegistration</code>.
     *
     * <p>If permitted, any changes to the returned <code>Collection</code> must not
     * affect this <code>FilterRegistration</code>.
     *
     * @return a (possibly empty) <code>Collection</code> of the currently
     * available servlet name mappings of the Filter represented by this
     * <code>FilterRegistration</code>
     */
    @Override
    public Collection<String> getServletNameMappings() {
        return ctx.getServletNameFilterMappings(getName());
    }

    /**
     * Adds a filter mapping with the given url patterns and dispatcher
     * types for the Filter represented by this FilterRegistration.
     *
     * <p>Filter mappings are matched in the order in which they were
     * added.
     *
     * <p>If this method is called multiple times, each successive call
     * adds to the effects of the former.
     *
     * @param dispatcherTypes the dispatcher types of the filter mapping,
     * or null if the default <tt>DispatcherType.REQUEST</tt> is to be used
     * @param urlPatterns the url patterns of the filter mapping
     *
     * @throws IllegalArgumentException if <tt>urlPatterns</tt> is null or
     * empty
     * @throws IllegalStateException if the ServletContext from which this
     * FilterRegistration was obtained has already been initialized
     */
    public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes,
                                         String... urlPatterns) {
        addMappingForUrlPatterns(dispatcherTypes, true, urlPatterns);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes,
            boolean isMatchAfter, String... urlPatterns) {
        if (ctx.deployed) {
            throw new IllegalStateException("WebappContext has already been deployed");
        }

        if ((urlPatterns==null) || (urlPatterns.length==0)) {
            throw new IllegalArgumentException("'urlPatterns' is null or zero-length");
        }

        for (String urlPattern : urlPatterns) {
            FilterMap fmap = new FilterMap();
            fmap.setFilterName(getName());
            fmap.setURLPattern(urlPattern);
            fmap.setDispatcherTypes(dispatcherTypes);

            ctx.addFilterMap(fmap, isMatchAfter);
        }
    }

    /**
     * Gets the currently available URL pattern mappings of the Filter
     * represented by this <code>FilterRegistration</code>.
     *
     * <p>If permitted, any changes to the returned <code>Collection</code> must not
     * affect this <code>FilterRegistration</code>.
     *
     * @return a (possibly empty) <code>Collection</code> of the currently
     * available URL pattern mappings of the Filter represented by this
     * <code>FilterRegistration</code>
     */
    @Override
    public Collection<String> getUrlPatternMappings() {
        return ctx.getUrlPatternFilterMappings(getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAsyncSupported(boolean isAsyncSupported) {
        this.isAsyncSupported = isAsyncSupported;
    }
}
