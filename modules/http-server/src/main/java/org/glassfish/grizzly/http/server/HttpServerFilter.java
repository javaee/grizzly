/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.server.io.ReadHandler;
import org.glassfish.grizzly.http.server.util.HtmlHelper;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.monitoring.jmx.AbstractJmxMonitoringConfig;
import org.glassfish.grizzly.monitoring.jmx.JmxMonitoringAware;
import org.glassfish.grizzly.monitoring.jmx.JmxMonitoringConfig;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.utils.DelayedExecutor;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.glassfish.grizzly.memory.Buffers;

/**
 * Filter implementation to provide high-level HTTP request/response processing.
 */
public class HttpServerFilter extends BaseFilter
        implements JmxMonitoringAware<HttpServerProbe> {


    private final Attribute<Request> httpRequestInProcessAttr;
    private final DelayedExecutor.DelayQueue<Response> suspendedResponseQueue;

    private final HttpServer gws;

    /**
     * Web server probes
     */
    protected final AbstractJmxMonitoringConfig<HttpServerProbe> monitoringConfig =
            new AbstractJmxMonitoringConfig<HttpServerProbe>(HttpServerProbe.class) {

                @Override
                public JmxObject createManagementObject() {
                    return createJmxManagementObject();
                }

            };


    // ------------------------------------------------------------ Constructors


    public HttpServerFilter(final HttpServer webServer) {
        gws = webServer;
        DelayedExecutor delayedExecutor = webServer.getDelayedExecutor();
        suspendedResponseQueue = Response.createDelayQueue(delayedExecutor);
        httpRequestInProcessAttr = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.
                createAttribute("HttpServerFilter.Request");

    }


    // ----------------------------------------------------- Methods from Filter


    @SuppressWarnings({"unchecked", "ReturnInsideFinallyBlock"})
    @Override
    public NextAction handleRead(FilterChainContext ctx)
          throws IOException {
        final Object message = ctx.getMessage();
        final Connection connection = ctx.getConnection();

        if (message instanceof HttpPacket) {
            // Otherwise cast message to a HttpContent

            final HttpContent httpContent = (HttpContent) message;

            Request serviceRequest = httpRequestInProcessAttr.get(connection);

            if (serviceRequest == null) {
                // It's a new HTTP request
                HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();
                HttpResponsePacket response = request.getResponse();
                serviceRequest = Request.create();
                httpRequestInProcessAttr.set(connection, serviceRequest);
                final Response serviceResponse = Response.create();

                serviceRequest.initialize(serviceResponse, request, httpContent, ctx, this);
                final SuspendStatus suspendStatus = new SuspendStatus();

                serviceResponse.initialize(serviceRequest, response, ctx,
                        suspendedResponseQueue, suspendStatus);

                HttpServerProbeNotifier.notifyRequestReceive(this, connection,
                        serviceRequest);

                try {
                    ctx.setMessage(serviceResponse);

                    final HttpService httpService = gws.getHttpService();
                    if (httpService != null) {
                        httpService.doService(serviceRequest, serviceResponse);
                    }
                } catch (Throwable t) {
                    serviceRequest.getRequest().getProcessingState().setError(true);
                    
                    if (!response.isCommitted()) {
                        ByteBuffer b = HtmlHelper.getExceptionErrorPage("Internal Server Error", "Grizzly/2.0", t);
                        serviceResponse.reset();
                        serviceResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                        serviceResponse.setContentType("text/html");
                        serviceResponse.setCharacterEncoding("UTF-8");
                        MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();
                        Buffer buf = Buffers.wrap(mm, b);
                        serviceResponse.getOutputBuffer().writeBuffer(buf);
                    }
                } finally {
                    if (!suspendStatus.get()) {
                        afterService(connection, serviceRequest, serviceResponse);
                    } else {
                        if (serviceRequest.asyncInput()) {
                            return ctx.getSuspendingStopAction();
                        } else {
                            return ctx.getSuspendAction();
                        }
                    }
                }
            } else {
                // We're working with suspended HTTP request
                if (serviceRequest.asyncInput()) {
                    if (!serviceRequest.getInputBuffer().isFinished()) {

                        final Buffer content = httpContent.getContent();

                        if (!content.hasRemaining() || !serviceRequest.getInputBuffer().append(content)) {
                            if (!httpContent.isLast()) {
                                // need more data?
                                return ctx.getStopAction();
                            }

                        }
                        if (httpContent.isLast()) {
                            serviceRequest.getInputBuffer().finished();
                            // we have enough data? - terminate filter chain execution
                            final NextAction action = ctx.getSuspendAction();
                            ctx.recycle();
                            return action;
                        }
                    }
                    httpContent.recycle();
                }
            }
        } else { // this code will be run, when we resume after suspend
            final Response response = (Response) message;
            final Request request = response.getRequest();
            afterService(connection, request, response);
        }

        return ctx.getStopAction();
    }


    /**
     * Override the default implementation to notify the {@link ReadHandler},
     * if available, of any read error that has occurred during processing.
     * 
     * @param ctx event processing {@link FilterChainContext}
     * @param error error, which occurred during <tt>FilterChain</tt> execution
     */
    @Override
    public void exceptionOccurred(FilterChainContext ctx, Throwable error) {
        final Connection c = ctx.getConnection();

        final Request request =
                httpRequestInProcessAttr.get(c);

        if (request != null) {
            ReadHandler handler = request.getInputBuffer().getReadHandler();
            if (handler != null) {
                handler.onError(error);
            }
        }
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * {@inheritDoc}
     */
    @Override
    public JmxMonitoringConfig<HttpServerProbe> getMonitoringConfig() {
        return monitoringConfig;
    }


    // ------------------------------------------------------- Protected Methods


    protected JmxObject createJmxManagementObject() {
        return new org.glassfish.grizzly.http.server.jmx.HttpServerFilter(this);
    }


    // --------------------------------------------------------- Private Methods


    private void afterService(final Connection connection,
                              final Request request,
                              final Response response)
    throws IOException {

        httpRequestInProcessAttr.remove(connection);

        response.finish();

        HttpServerProbeNotifier.notifyRequestComplete(this,
                                                     connection,
                response);
        response.recycle();
        request.recycle();


    }

}
