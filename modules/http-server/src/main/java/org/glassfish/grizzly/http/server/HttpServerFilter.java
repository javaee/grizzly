/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
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
import java.util.logging.Logger;
import org.glassfish.grizzly.http.*;

import org.glassfish.grizzly.memory.Buffers;

/**
 * Filter implementation to provide high-level HTTP request/response processing.
 */
public class HttpServerFilter extends BaseFilter
        implements JmxMonitoringAware<HttpServerProbe> {

    private static final Logger LOGGER = Grizzly.logger(HttpServerFilter.class);

    private final Attribute<Request> httpRequestInProcessAttr;
    final Attribute<Boolean> reregisterForReadAttr;
    
    private final DelayedExecutor.DelayQueue<Response> suspendedResponseQueue;

    private volatile HttpHandler httpHandler;

    private final ServerFilterConfiguration config;
    
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


    public HttpServerFilter(final ServerFilterConfiguration config,
            final DelayedExecutor delayedExecutor) {
        this.config = config;
        suspendedResponseQueue = Response.createDelayQueue(delayedExecutor);
        httpRequestInProcessAttr = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.
                createAttribute("HttpServerFilter.Request");
        reregisterForReadAttr = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.
                createAttribute("HttpServerFilter.reregisterForReadAttr");
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    public void setHttpHandler(final HttpHandler httpHandler) {
        this.httpHandler = httpHandler;
    }

    public ServerFilterConfiguration getConfiguration() {
        return config;
    }
    
    // ----------------------------------------------------- Methods from Filter


    @SuppressWarnings({"unchecked", "ReturnInsideFinallyBlock"})
    @Override
    public NextAction handleRead(final FilterChainContext ctx)
          throws IOException {
        final Object message = ctx.getMessage();
        final Connection connection = ctx.getConnection();

        if (HttpPacket.isHttp(message)) {

            // Otherwise cast message to a HttpContent
            final HttpContent httpContent = (HttpContent) message;
            
            Request handlerRequest = httpRequestInProcessAttr.get(connection);

            if (handlerRequest == null) {
                // It's a new HTTP request
                final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();
                final HttpResponsePacket response = request.getResponse();                
                handlerRequest = Request.create();
                httpRequestInProcessAttr.set(connection, handlerRequest);
                final Response handlerResponse = Response.create();

                handlerRequest.initialize(handlerResponse, request, ctx, this);
                final SuspendStatus suspendStatus = handlerResponse.initialize(
                        handlerRequest, response, ctx, suspendedResponseQueue, this);

                HttpServerProbeNotifier.notifyRequestReceive(this, connection,
                        handlerRequest);

                try {
                    ctx.setMessage(handlerResponse);

                    if (request.getMethod() == Method.TRACE) {
                        onTraceRequest(handlerRequest, handlerResponse);
                    } else {
                        final HttpHandler httpHandlerLocal = httpHandler;
                        if (httpHandlerLocal != null) {
                            httpHandlerLocal.doHandle(handlerRequest, handlerResponse);
                        }
                    }
                } catch (Throwable t) {
                    handlerRequest.getRequest().getProcessingState().setError(true);
                    
                    if (!response.isCommitted()) {
                        final ByteBuffer b = HtmlHelper.getExceptionErrorPage("Internal Server Error", "Grizzly/2.0", t);
                        handlerResponse.reset();
                        handlerResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                        handlerResponse.setContentType("text/html");
                        handlerResponse.setCharacterEncoding("UTF-8");
                        final MemoryManager mm = ctx.getMemoryManager();
                        final Buffer buf = Buffers.wrap(mm, b);
                        handlerResponse.getOutputBuffer().writeBuffer(buf);
                    }
                } finally {
                    if (!suspendStatus.get()) {
                        return afterService(ctx, connection,
                                handlerRequest, handlerResponse);
                    } else {
                        return ctx.getSuspendAction();
                    }
                }
            } else {
                // We're working with suspended HTTP request
                try {
                    if (!handlerRequest.getInputBuffer().append(httpContent)) {
                        // we don't want this thread/context to reset
                        // OP_READ on Connection

                        // we have enough data? - terminate filter chain execution
                        final NextAction action = ctx.getSuspendAction();
                        ctx.completeAndRecycle();
                        return action;
                    }
                } finally {
                    httpContent.recycle();
                }
            }
        } else { // this code will be run, when we resume the context
            if (Boolean.TRUE.equals(reregisterForReadAttr.remove(ctx))) {
                // Do we want to reregister OP_READ to get more data async?
                ctx.suspend();
                return ctx.getForkAction();
            } else {
                // We're finishing the request processing
                final Response response = (Response) message;
                final Request request = response.getRequest();
                return afterService(ctx, connection,
                        request, response);
            }
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
            final ReadHandler handler = request.getInputBuffer().getReadHandler();
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

    protected void onTraceRequest(final Request request,
            final Response response) throws IOException {
        if (config.isTraceEnabled()) {
            HtmlHelper.writeTraceMessage(request, response);
        } else {
            response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
        }
    }

    
    // --------------------------------------------------------- Private Methods


    private NextAction afterService(
            final FilterChainContext ctx,
            final Connection connection,
            final Request request,
            final Response response)
            throws IOException {

        httpRequestInProcessAttr.remove(connection);

        response.finish();
        request.onAfterService();
        
        HttpServerProbeNotifier.notifyRequestComplete(this, connection, response);
        
        final HttpRequestPacket httpRequest = request.getRequest();
        final boolean isBroken = httpRequest.isContentBroken();
        
        // Suspend state is cancelled - it means normal processing might have
        // been broken. We don't want to reuse Request and Response in this state,
        // cause there still might be threads referencing them.
        if (response.suspendState.get() != Response.SuspendState.CANCELLED) {
            response.recycle();
            request.recycle();
        }
        
        if (isBroken) {
            // if content is broken - we're not able to distinguish
            // the end of the message - so stop processing any input data on
            // this connection (connection is being closed by
            // {@link org.glassfish.grizzly.http.HttpServerFilter#handleEvent(...)}
            final NextAction suspendNextAction = ctx.getSuspendAction();
            ctx.completeAndRecycle();
            return suspendNextAction;
        }
        
        return ctx.getStopAction();
    }
}
