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

import com.sun.grizzly.Grizzly;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpPacket;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.http.server.adapter.GrizzlyAdapter;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * TODO:
 *   JMX
 *   Statistics
 *   Interceptor support (necessary for FileCache?)
 */
public class WebServerFilter extends BaseFilter {

    private final ScheduledExecutorService scheduledExecutorService;
    private final GrizzlyAdapter adapter;


    private KeepAliveStats keepAliveStats = null;

    private static Attribute<Integer> keepAliveCounterAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            "connection-keepalive-counter", 0);
    

    // ------------------------------------------------------------ Constructors


    public WebServerFilter(GrizzlyWebServer webServer) {
        adapter = webServer.getAdapter();
        scheduledExecutorService = webServer.getScheduledExecutorService();
    }


    // ----------------------------------------------------- Methods from Filter


    @Override
    public NextAction handleRead(FilterChainContext ctx)
          throws IOException {

        final Object message = ctx.getMessage();

        if (message instanceof HttpPacket) {
            // Otherwise cast message to a HttpContent
            final HttpContent httpContent = (HttpContent) message;

            HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();
            HttpResponsePacket response = request.getResponse();
            final GrizzlyRequest grizzlyRequest = GrizzlyRequest.create();
            grizzlyRequest.initialize(request, ctx);
            final GrizzlyResponse grizzlyResponse = GrizzlyResponse.create();
            final SuspendStatus suspendStatus = new SuspendStatus();

            grizzlyResponse.initialize(grizzlyRequest, response, ctx,
                    scheduledExecutorService, suspendStatus);

            try {
                ctx.setMessage(grizzlyResponse);
                adapter.service(grizzlyRequest, grizzlyResponse);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (!suspendStatus.get()) {
                    afterService(grizzlyRequest, grizzlyResponse);
                } else {
                    return ctx.getSuspendAction();
                }
            }
        } else {
            final GrizzlyResponse grizzlyResponse = (GrizzlyResponse) message;
            final GrizzlyRequest grizzlyRequest = grizzlyResponse.getRequest();
            afterService(grizzlyRequest, grizzlyResponse);
        }


        return ctx.getStopAction();
    }

    private void afterService(GrizzlyRequest grizzlyRequest,
            final GrizzlyResponse grizzlyResponse) throws IOException {
        grizzlyResponse.finish();
        grizzlyRequest.recycle();
        grizzlyResponse.recycle();
    }

}
