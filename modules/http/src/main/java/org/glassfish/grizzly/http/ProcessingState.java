/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

/**
 * Maintains semantic state necessary to proper HTTP processing.
 */
public final class ProcessingState {

    /**
     * <p>
     * This flag controls the semantics of the Connection response header.
     * </p>
     */
    boolean keepAlive = false;

    /**
     * <p>
     * Indicates if an error occurred during request/response processing.
     * </p>
     */
    boolean error;

    /**
     * <p>
     * References {@link HttpContext} associated with the processing.
     * </p>
     */
    HttpContext httpContext;

    /**
     * <p>
     * This flag indicates whether error occurred during the HTTP processing.
     * </p>
     *
     * @return <tt>true</tt>, if error occurred during the HTTP processing, or
     * <tt>false</tt> otherwise.
     */
    public boolean isError() {
        return error;
    }

    /**
     * <p>
     * This flag indicates whether error occurred during the HTTP processing.
     * </p>
     *
     * @param error <tt>true</tt>, if error occurred during the HTTP processing, or
     * <tt>false</tt> otherwise.
     */
    public void setError(boolean error) {
        this.error = error;
    }

    /**
     * <p>
     * Method returns <tt>true</tt> only if the connection is in keep-alive mode
     * and there was no error occurred during the packet processing.
     * </p>
     *
     * @return <tt>true</tt> only if the connection is in keep-alive mode
     * and there was no error occurred during the packet processing.
     */
    public boolean isStayAlive() {
        return keepAlive && !error;
    }
    
    /**
     * <p>
     * This flag controls the connection keep-alive feature.
     * </p>
     *
     * @return <tt>true</tt> if connection may work in keep-alive mode or <tt>false</tt> otherwise.
     */
    public boolean isKeepAlive() {
        return keepAlive;
    }

    /**
     * <p>
     * This flag controls the connection keep-alive feature.
     * </p>
     *
     * @param keepAlive <tt>true</tt> if connection may work in keep-alive mode or <tt>false</tt> otherwise.
     */
    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    /**
     * <p>
     * Returns {@link HttpContext} associated with the processing.
     * </p>
     *
     * @return {@link HttpContext} associated with the processing.
     */    
    public HttpContext getHttpContext() {
        return httpContext;
    }

    /**
     * <p>
     * Sets the {@link HttpContext} associated with the processing.
     * </p>
     *
     * @param httpContext {@link HttpContext}.
     */
    public void setHttpContext(final HttpContext httpContext) {
        this.httpContext = httpContext;
    }

    /**
     * <p>
     * Resets values to their initial states.
     * </p>
     */
    public void recycle() {
        keepAlive = false;
        error = false;
        httpContext = null;
    }

}
