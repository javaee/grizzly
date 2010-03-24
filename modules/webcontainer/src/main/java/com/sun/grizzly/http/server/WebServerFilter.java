/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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

package com.sun.grizzly.http.server;

import com.sun.grizzly.EmptyCompletionHandler;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpRequest;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.CompletionHandler;
import com.sun.grizzly.tcp.InputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;
import com.sun.grizzly.tcp.http11.filters.BufferedInputFilter;
import com.sun.grizzly.tcp.http11.filters.ChunkedInputFilter;
import com.sun.grizzly.tcp.http11.filters.ChunkedOutputFilter;
import com.sun.grizzly.tcp.http11.filters.GzipOutputFilter;
import com.sun.grizzly.tcp.http11.filters.IdentityInputFilter;
import com.sun.grizzly.tcp.http11.filters.IdentityOutputFilter;
import com.sun.grizzly.tcp.http11.filters.VoidInputFilter;
import com.sun.grizzly.tcp.http11.filters.VoidOutputFilter;

import java.io.IOException;

import static com.sun.grizzly.http.server.embed.GrizzlyWebServer.ServerConfiguration;

/**
 * TODO:
 *   JMX
 *   Statistics
 *   Interceptor support (necessary for FileCache?)
 */
public class WebServerFilter extends BaseFilter {

    private final ServerConfiguration config;


    private KeepAliveStats keepAliveStats = null;

    private static Attribute<Integer> keepAliveCounterAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            "connection-keepalive-counter", 0);
    

    // ------------------------------------------------------------ Constructors


    public WebServerFilter(ServerConfiguration config) {

        this.config = config;

    }


    // ----------------------------------------------------- Methods from Filter


    @Override public NextAction handleRead(FilterChainContext ctx)
          throws IOException {

        // Otherwise cast message to a HttpContent
        final HttpContent httpContent = (HttpContent) ctx.getMessage();

        if (!httpContent.isLast()) {
            // no action until we have all content
            return ctx.getStopAction();

        }


        //prepareProcessing(ctx, request, response);
        Adapter adapter = config.getAdapter();

        try {
        //    adapter.service(request, response);
        } catch (Exception e) {
            throw new RuntimeException(e);   
        }

        return ctx.getSuspendAction();

    }


    // --------------------------------------------------------- Private Methods


    private void prepareProcessing(FilterChainContext ctx,
                                   Request request,
                                   Response response) {

        InternalInputBuffer inputBuffer = new InternalInputBuffer(request, Constants.DEFAULT_REQUEST_BUFFER_SIZE);

        SocketChannelOutputBuffer outputBuffer =
              new SocketChannelOutputBuffer(response,
                                            ctx.getConnection(),
                                            Constants.DEFAULT_HTTP_HEADER_BUFFER_SIZE,
                                            true);


        request.setInputBuffer(inputBuffer);

        response.setOutputBuffer(outputBuffer);
        request.setResponse(response);

        
    }


}
