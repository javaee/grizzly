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

import java.io.IOException;
import java.nio.ByteBuffer;

import com.sun.grizzly.Context;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.util.buf.ByteChunk;

/**
 * Specialized ProtocolFilter that properly configure the Http Adapter on the fly.
 *
 * @author Jeanfrancois Arcand
 */
public class HttpProtocolFilter extends AbstractHttpHandler
    implements ProtocolFilter {
    /**
     * The Grizzly's wrapped ProtocolFilter.
     */
    private final ProtocolFilter wrappedFilter;
    private static final byte[] errorBody = HttpUtils.getErrorPage("Glassfish/v3", "HTTP Status 404");

    public HttpProtocolFilter(final ProtocolFilter wrappedFilter, final GrizzlyEmbeddedHttp http) {
        super(http);
        this.wrappedFilter = wrappedFilter;
        final StaticResourcesAdapter adapter = new StaticResourcesAdapter() {
            @Override
            protected void customizedErrorPage(final Request req,
                final Response res) throws Exception {
                final ByteChunk chunk = new ByteChunk();
                chunk.setBytes(errorBody, 0, errorBody.length);
                res.setContentLength(errorBody.length);
                res.sendHeaders();
                res.doWrite(chunk);
            }
        };
        adapter.setRootFolder(GrizzlyEmbeddedHttp.getWebAppRootPath());
        setFallbackContextRootInfo(new ContextRootInfo(adapter, null, null));

    }

    public boolean execute(final Context context) throws IOException {
        final WorkerThread thread = (WorkerThread) Thread.currentThread();
        final ByteBuffer byteBuffer = thread.getByteBuffer();
        return initializeHttpRequestProcessing(context, byteBuffer) && wrappedFilter.execute(context);
    }

    /**
     * Execute the wrapped ProtocolFilter.
     */
    public boolean postExecute(final Context ctx) throws IOException {
        return wrappedFilter.postExecute(ctx);
    }
}
