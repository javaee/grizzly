/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server;

/**
 * Class representing {@link HttpHandler} registration information on a
 * {@link HttpServer}.
 * An instance of the class could be created either from {@link String} using
 * {@link #fromString(java.lang.String)} method, or builder {@link #builder()}.
 * 
 * @author Alexey Stashok
 */
public class HttpHandlerRegistration {
    public static final HttpHandlerRegistration ROOT =
            HttpHandlerRegistration.builder()
            .contextPath("")
            .urlPattern("/")
            .build();
    
    /**
     * @return the <tt>HttpHandlerRegistration</tt> builder.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * @return the <tt>HttpHandlerRegistration</tt> builder.
     * 
     * @deprecated typo :(
     */
    public static Builder bulder() {
        return builder();
    }

    /**
     * Create a registration from the mapping {@link String}.
     * The part of the <tt>mapping</tt> before the second slash '/' occurrence will
     * be treated as <tt>context-path</tt> and the remainder will be treated as
     * a <tt>url-pattern</tt>.
     * For example:
     *      1) "" will be treated as context-path("") and url-pattern("");
     *      2) "/" will be treated as context-path("") and url-pattern("/");
     *      3) "/a/b/c" will be treated as context-path("/a") and url-pattern("/b/c");
     *      4) "/*" will be treated as context-path("") and url-pattern("/*")
     *      5) "*.jpg" will be treated as context-path("") and url-pattern("*.jpg")
     * 
     * @param mapping the {@link String}
     * @return {@link HttpHandlerRegistration}
     */
    public static HttpHandlerRegistration fromString(final String mapping) {
        if (mapping == null) {
            return ROOT;
        }
        
        final String contextPath = getContextPath(mapping);
        return new HttpHandlerRegistration(contextPath,
                getWrapperPath(contextPath, mapping));
    }
    
    private final String contextPath;
    private final String urlPattern;

    private HttpHandlerRegistration(final String contextPath,
            final String urlPattern) {
        this.contextPath = contextPath;
        this.urlPattern = urlPattern;
    }
    
    /**
     * @return <tt>context-path</tt> part of the registration
     */
    public String getContextPath() {
        return contextPath;
    }
    
    /**
     * @return <tt>url-pattern</tt> part of the registration
     */
    public String getUrlPattern() {
        return urlPattern;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 41 * hash + this.contextPath.hashCode();
        hash = 41 * hash + this.urlPattern.hashCode();
        return hash;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        
        final HttpHandlerRegistration other = (HttpHandlerRegistration) obj;
        
        return this.contextPath.equals(other.contextPath) &&
                this.urlPattern.equals(other.urlPattern);
    }
    
    private static String getWrapperPath(String ctx, String mapping) {

        if (mapping.indexOf("*.") > 0) {
            return mapping.substring(mapping.lastIndexOf("/") + 1);
        } else if (ctx.length() != 0) {
            return mapping.substring(ctx.length());
        } else if (mapping.startsWith("//")) {
            return mapping.substring(1);
        } else {
            return mapping;
        }
    }

    private static String getContextPath(String mapping) {
        String ctx;
        int slash = mapping.indexOf("/", 1);
        if (slash != -1) {
            ctx = mapping.substring(0, slash);
        } else {
            ctx = mapping;
        }

        if (ctx.startsWith("/*") || ctx.startsWith("*")) {
            ctx = "";
        }

        // Special case for the root context
        if (ctx.equals("/")) {
            ctx = "";
        }

        return ctx;
    }
    
    public static class Builder {
        private String contextPath;
        private String urlPattern;

        public Builder contextPath(final String contextPath) {
            this.contextPath = contextPath;
            return this;
        }

        public Builder urlPattern(final String urlPattern) {
            this.urlPattern = urlPattern;
            return this;
        }
        
        public HttpHandlerRegistration build() {
            if (contextPath == null) {
                contextPath = "";
            }
            
            if (urlPattern == null) {
                urlPattern = "/";
            }
            
            return new HttpHandlerRegistration(contextPath, urlPattern);
        }
    }
}
