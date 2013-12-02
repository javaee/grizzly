/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HeaderValue;
import org.glassfish.grizzly.http.util.MimeHeaders;

/**
 * Common interface for {@link HttpPacket}s, which contain mimeheaders.
 * 
 * @author Alexey Stashok
 */
public interface MimeHeadersPacket {
    /**
     * Get all {@link MimeHeaders}, associated with the <tt>HttpHeader</tt>.
     *
     * @return all {@link MimeHeaders}, associated with the <tt>HttpHeader</tt>
     */
    MimeHeaders getHeaders();

    /**
     * Get the value, of the specific HTTP mime header.
     * @param name the mime header name
     *
     * @return the value, of the specific HTTP mime header
     */
    String getHeader(String name);

    /**
     * Get the value, of the specific HTTP mime header.
     * @param header the mime {@link Header}
     *
     * @return the value, of the specific HTTP mime header
     *
     * @since 2.1.2
     */
    String getHeader(final Header header);

    /**
     * Set the value, of the specific HTTP mime header.
     *
     * @param name the mime header name
     * @param value the mime header value
     */
    void setHeader(String name, String value);

    /**
     * Set the value, of the specific HTTP mime header.
     *
     * @param name the mime header name
     * @param value the mime header value
     * 
     * @since 2.3.8
     */
    void setHeader(String name, HeaderValue value);
    
    /**
     * Set the value, of the specific HTTP mime header.
     *
     * @param header the mime {@link Header}
     * @param value the mime header value
     *
     * @since 2.1.2
     */
    void setHeader(final Header header, String value);

    /**
     * Set the value, of the specific HTTP mime header.
     *
     * @param header the mime {@link Header}
     * @param value the mime header value
     *
     * @since 2.3.8
     */
    void setHeader(final Header header, HeaderValue value);
    
    /**
     * Add the HTTP mime header.
     *
     * @param name the mime header name
     * @param value the mime header value
     */
    void addHeader(String name, String value);

    /**
     * Add the HTTP mime header.
     *
     * @param name the mime header name
     * @param value the mime header value
     * 
     * @since 2.3.8
     */
    void addHeader(String name, HeaderValue value);
    
    /**
     * Add the HTTP mime header.
     *
     * @param header the mime {@link Header}
     * @param value the mime header value
     *
     * @since 2.1.2
     */
    void addHeader(final Header header, final String value);

    /**
     * Add the HTTP mime header.
     *
     * @param header the mime {@link Header}
     * @param value the mime header value
     *
     * @since 2.3.8
     */
    void addHeader(final Header header, final HeaderValue value);
    
    /**
     * Returns <tt>true</tt>, if the mime header with the specific name is present
     * among the <tt>HttpHeader</tt> mime headers, or <tt>false</tt> otherwise.
     *
     * @param name the mime header name
     *
     * @return <tt>true</tt>, if the mime header with the specific name is present
     * among the <tt>HttpHeader</tt> mime headers, or <tt>false</tt> otherwise
     */
    boolean containsHeader(String name);

    /**
     * Returns <tt>true</tt>, if the mime {@link Header} is present
     * among the <tt>HttpHeader</tt> mime headers, otherwise returns <tt>false</tt>.
     *
     * @param header the mime {@link Header}
     *
     * @return <tt>true</tt>, if the mime {@link Header} is present
     * among the <tt>HttpHeader</tt> mime headers, otherwise returns <tt>false</tt>
     *
     * @since 2.1.2
     */
    boolean containsHeader(final Header header);

}
