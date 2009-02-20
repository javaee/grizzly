/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.config;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.http.HttpProtocolChain;
import com.sun.grizzly.http.HttpWorkerThread;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.buf.UDecoder;
import com.sun.grizzly.util.http.HttpRequestURIDecoder;
import com.sun.grizzly.util.http.mapper.Mapper;
import com.sun.grizzly.util.http.mapper.MappingData;

/**
 * Created Feb 19, 2009
 *
 * @author <a href="mailto:justin.lee@sun.com">Justin Lee</a>
 */
public class GrizzlyMappingAdapter extends StaticResourcesAdapter {
    protected ContextMapper mapper;
    private GrizzlyEmbeddedHttp grizzlyEmbeddedHttp;
    private ConcurrentLinkedQueue<HttpParserState> parserStates;
    protected UDecoder urlDecoder = new UDecoder();
    private String defaultHostName = "server";

    public GrizzlyMappingAdapter() {
        parserStates = new ConcurrentLinkedQueue<HttpParserState>();
        logger = GrizzlyEmbeddedHttp.logger();
        setRootFolder(GrizzlyEmbeddedHttp.getWebAppRootPath());
    }

    /**
     * Set the default host that will be used when we map.
     *
     * @param defaultHost
     */
    protected void setDefaultHost(final String defaultHost) {
        defaultHostName = defaultHost;
    }

    /**
     * Set the {@link Mapper} instance used for mapping the container and its associated {@link Adapter}.
     *
     * @param mapper
     */
    protected void setMapper(final ContextMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Configure the {@link Mapper}.
     */
    protected synchronized void configureMapper() {
        mapper.setDefaultHostName(defaultHostName);
        // Container deployed have the right to override the default setting.
        Mapper.setAllowReplacement(true);
    }

    public boolean map(final SelectionKey selectionKey, final ByteBuffer byteBuffer,
        final HttpProtocolChain protocolChain, final List<ProtocolFilter> defaultProtocolFilters,
        final ContextRootInfo fallbackContextRootInfo)
        throws Exception {
        HttpParserState state = parserStates.poll();
        if (state == null) {
            state = new HttpParserState();
        } else {
            state.reset();
        }
        state.setBuffer(byteBuffer);
        byte[] contextBytes = null;
        byte[] hostBytes = null;
        try {
            // Read the request line, and parse the context root by removing
            // all trailling // or ?
            contextBytes = HttpUtils.readRequestLine(selectionKey, state, InputReader.getDefaultReadTimeout());
            if (contextBytes != null) {
                state.setState(0);
                // Read available bytes and try to find the host header.
                hostBytes = HttpUtils.readHost(selectionKey, state, InputReader.getDefaultReadTimeout());
            }
        } finally {
            parserStates.offer(state);
        }
        // No bytes then fail.
        if (contextBytes == null) {
            return false;
        }
        final MessageBytes decodedURI = MessageBytes.newInstance();
        decodedURI.setBytes(contextBytes, 0, contextBytes.length);
        final MessageBytes hostMB = MessageBytes.newInstance();
        if (hostBytes != null) {
            hostMB.setBytes(hostBytes, 0, hostBytes.length);
        }
        // Decode the request to make sure this is not an attack like a directory traversal vulnerability.
        try {
            HttpRequestURIDecoder
                .decode(decodedURI, urlDecoder, (String) grizzlyEmbeddedHttp.getProperty("uriEncoding"), null);
        } catch (Exception ex) {
            // We fail to decode the request, hence we don't service it.
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Invalid url", ex);
            }
            return false;
        }
        // Parse the host. If not found, add it based on the current host.
        HttpUtils.parseHost(hostMB, ((SocketChannel) selectionKey.channel()).socket());
        //TODO: Use ThreadAttachment instead.
        final MappingData mappingData = new MappingData();
        // Map the request to its Adapter/Container and also its Servlet if the request is targetted to the CoyoteAdapter.
        mapper.map(hostMB, decodedURI, mappingData);
        Adapter adapter = null;
        ContextRootInfo contextRootInfo = null;
        // First, let's see if the request is NOT for the CoyoteAdapter, but for
        // another adapter like grail/rail.
        if (mappingData.context != null && mappingData.context instanceof ContextRootInfo) {
            contextRootInfo = (ContextRootInfo) mappingData.context;
            adapter = contextRootInfo.getAdapter();
        } else if (mappingData.context != null
            && "com.sun.enterprise.web.WebModule".equals(mappingData.context.getClass().getName())) {
            // Copy the decoded bytes so it can be later re-used by the CoyoteAdapter
            final MessageBytes fullDecodedUri = MessageBytes.newInstance();
            fullDecodedUri.duplicate(decodedURI);
            fullDecodedUri.toBytes();
            // We bind the current information to the WorkerThread so CoyoteAdapter
            // can read it and avoid trying to map. Note that we cannot re-use
            // the object as Grizzly ARP might used them from a different Thread.
            final WorkerThread workerThread = (WorkerThread) Thread.currentThread();
            workerThread.getAttachment().setAttribute("mappingData", mappingData);
            workerThread.getAttachment().setAttribute("decodedURI", fullDecodedUri);
            adapter = mapper.getAdapter();
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("MAP (" + this + ") contextRoot: " + new String(contextBytes) + " defaultProtocolFilters: "
                + defaultProtocolFilters + " fallback: " + fallbackContextRootInfo + " adapter: " + adapter
                + " mappingData.context " + mappingData.context);
        }
        if (adapter == null && fallbackContextRootInfo != null) {
            adapter = fallbackContextRootInfo.getAdapter();
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Fallback adapter is taken: " + adapter);
            }
        }
        if (adapter == null) {
            return false;
        }
        bindAdapter(adapter);
        postMap(protocolChain, defaultProtocolFilters, fallbackContextRootInfo, contextRootInfo);
        return true;
    }

    protected void postMap(final HttpProtocolChain protocolChain, final List<ProtocolFilter> defaultProtocolFilters,
        final ContextRootInfo fallbackContextRootInfo, final ContextRootInfo contextRootInfo) {
    }

    /**
     * Return the Container associated with the current context-root, null if not found. If the Adapter is found, bind
     * it to the current ProcessorTask.
     */
    private Adapter bindAdapter(final Adapter adapter) {
        // If no Adapter has been found, add a default one. This is the equivalent
        // of having virtual host.
        bindProcessorTask(adapter);
        return adapter;
    }

    // -------------------------------------------------------------------- //
    /**
     * Bind to the current WorkerThread the proper instance of ProcessorTask.
     *
     * @param adapter The Adapter associated with the ProcessorTask
     */
    private void bindProcessorTask(final Adapter adapter) {
        final HttpWorkerThread workerThread = (HttpWorkerThread) Thread.currentThread();
        ProcessorTask processorTask = workerThread.getProcessorTask();
        if (processorTask == null) {
            try {
                //TODO: Promote setAdapter to ProcessorTask?
                processorTask = grizzlyEmbeddedHttp.getProcessorTask();
            } catch (ClassCastException ex) {
                logger.log(Level.SEVERE, "Invalid ProcessorTask instance", ex);
            }
            workerThread.setProcessorTask(processorTask);
        }
        processorTask.setAdapter(adapter);
    }
}