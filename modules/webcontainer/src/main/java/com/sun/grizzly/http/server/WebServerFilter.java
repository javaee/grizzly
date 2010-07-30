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

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.monitoring.MonitoringAware;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpPacket;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.monitoring.MonitoringConfig;
import com.sun.grizzly.monitoring.MonitoringConfigImpl;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * TODO:
 *   JMX
 *   Statistics
 */
public class WebServerFilter extends BaseFilter
        implements MonitoringAware<WebServerProbe> {

    private final Attribute<GrizzlyRequest> grizzlyRequestInProcessAttr;

    private final ScheduledExecutorService scheduledExecutorService;
    private final GrizzlyAdapter adapter;

    private static Attribute<Integer> keepAliveCounterAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            "connection-keepalive-counter", 0);
    
    /**
     * Web server probes
     */
    protected final MonitoringConfigImpl<WebServerProbe> monitoringConfig =
            new MonitoringConfigImpl<WebServerProbe>(WebServerProbe.class);

    // ------------------------------------------------------------ Constructors


    public WebServerFilter(GrizzlyWebServer webServer) {

        adapter = webServer.getAdapter();
        scheduledExecutorService = webServer.getScheduledExecutorService();
        this.grizzlyRequestInProcessAttr =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                "WebServerFilter.GrizzlyRequest");

    }


    // ----------------------------------------------------- Methods from Filter


    @Override
    public NextAction handleRead(FilterChainContext ctx)
          throws IOException {
        final Object message = ctx.getMessage();
        final Connection connection = ctx.getConnection();

        if (message instanceof HttpPacket) {
            // Otherwise cast message to a HttpContent
            final HttpContent httpContent = (HttpContent) message;

            GrizzlyRequest grizzlyRequest = grizzlyRequestInProcessAttr.get(connection);

            if (grizzlyRequest == null) {
                HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();
                HttpResponsePacket response = request.getResponse();
                grizzlyRequest = GrizzlyRequest.create();
                grizzlyRequest.initialize(request, httpContent, ctx, this);
                final GrizzlyResponse grizzlyResponse = GrizzlyResponse.create();
                final SuspendStatus suspendStatus = new SuspendStatus();

                grizzlyResponse.initialize(grizzlyRequest, response, ctx,
                        scheduledExecutorService, suspendStatus);
                grizzlyRequestInProcessAttr.set(connection, grizzlyRequest);

                WebServerProbeNotificator.notifyRequestReceived(this, connection,
                        grizzlyRequest);

                try {
                    ctx.setMessage(grizzlyResponse);
                    if (adapter != null) {
                        adapter.doService(grizzlyRequest, grizzlyResponse);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    if (!suspendStatus.get()) {
                        afterService(connection, grizzlyRequest, grizzlyResponse);
                    } else {
                        if (grizzlyRequest.asyncInput()) {
                            return ctx.getSuspendingStopAction();
                        } else {
                            return ctx.getSuspendAction();
                        }
                    }
                }
            } else {
                if (grizzlyRequest.asyncInput()) {
                    if (!grizzlyRequest.getInputBuffer().isFinished()) {

                        final Buffer content = httpContent.getContent();

                        if (!content.hasRemaining() || !grizzlyRequest.getInputBuffer().append(content)) {
                            if (!httpContent.isLast()) {
                                // need more data?
                                return ctx.getStopAction();
                            }

                        }
                        if (httpContent.isLast()) {
                            grizzlyRequest.getInputBuffer().finished();
                            // we have enough data? - terminate filter chain execution
                            final NextAction action = ctx.getSuspendAction();
                            ctx.recycle();
                            return action;
                        }
                    } 
                }
            }
        } else { // this code will be run, when we resume after suspend
            final GrizzlyResponse grizzlyResponse = (GrizzlyResponse) message;
            final GrizzlyRequest grizzlyRequest = grizzlyResponse.getRequest();
            afterService(connection, grizzlyRequest, grizzlyResponse);
        }

        return ctx.getStopAction();
    }

    private void afterService(final Connection connection,
                              final GrizzlyRequest grizzlyRequest,
                              final GrizzlyResponse grizzlyResponse)
    throws IOException {

        grizzlyRequestInProcessAttr.remove(connection);
        grizzlyResponse.finish();

        final HttpRequestPacket request = grizzlyRequest.getRequest();
        final boolean isExpectContent = request.isExpectContent();

        if (isExpectContent) {
            request.setSkipRemainder(true);
        }

        WebServerProbeNotificator.notifyRequestCompleted(this, connection,
                grizzlyResponse);
        
        grizzlyRequest.recycle(!isExpectContent);
        grizzlyResponse.recycle(!isExpectContent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MonitoringConfig<WebServerProbe> getMonitoringConfig() {
        return monitoringConfig;
    }
}
