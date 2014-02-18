/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.OutputSink;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.filterchain.FilterChainContext;

/**
 * Represents a single logical HTTP transaction.  The target storage provided
 * to the constructor provides a way to look up this transaction at any point
 * in the FilterChain execution.
 *
 * @since 2.3
 */
public class HttpContext implements AttributeStorage {

    private static final Attribute<HttpContext> HTTP_CONTEXT_ATTR =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(HttpContext.class.getName());
    private final AttributeStorage contextStorage;
    private final OutputSink outputSink;
    private final Closeable closeable;
    private final HttpRequestPacket request;
    
    protected HttpContext(final AttributeStorage attributeStorage,
            final OutputSink outputSink, final Closeable closeable,
            final HttpRequestPacket request) {
        this.contextStorage = attributeStorage;
        this.closeable = closeable;
        this.outputSink = outputSink;
        this.request = request;
    }

    // ---------------------------------------------------------- Public Methods
    
    public HttpRequestPacket getRequest() {
        return request;
    }

    public HttpContext attach(final FilterChainContext ctx) {
        HTTP_CONTEXT_ATTR.set(ctx, this);
        return this;
    }
    
    @Override
    public final AttributeHolder getAttributes() {
        return contextStorage.getAttributes();
    }

    public AttributeStorage getContextStorage() {
        return contextStorage;
    }

    public OutputSink getOutputSink() {
        return outputSink;
    }
    
    public Closeable getCloseable() {
        return closeable;
    }

    public void close() {
        closeable.closeSilently();
    }
    
    public static HttpContext newInstance(
            final AttributeStorage attributeStorage,
            final OutputSink outputSink,
            final Closeable closeable,
            final HttpRequestPacket request) {
        return new HttpContext(attributeStorage,
                outputSink, closeable, request);
    }

    public static HttpContext get(final FilterChainContext ctx) {
        return HTTP_CONTEXT_ATTR.get(ctx);
    }
}
