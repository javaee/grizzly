/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.config;

import com.sun.grizzly.Context;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.config.dom.HttpRedirect;
import com.sun.grizzly.http.portunif.HttpRedirector;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.WorkerThread;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import javax.net.ssl.SSLEngine;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.config.ConfigBeanProxy;

/**
 *
 * @author Alexey Stashok
 */
public class HttpRedirectFilter implements ProtocolFilter,
        ConfigAwareElement {

    private Integer redirectPort;

    // default to true to retain compatibility with legacy redirect declarations.
    private Boolean secure;


    // --------------------------------------------- Methods from ProtocolFilter


    public boolean execute(Context ctx) throws IOException {
        final WorkerThread thread = (WorkerThread) Thread.currentThread();
        final SSLEngine sslEngine = thread.getSSLEngine();

        final boolean redirectToSecure;
        if (secure != null) { // if secure is set - we use it
            redirectToSecure = secure;
        } else {  // if secure is not set - use secure settings opposite to the current request
            redirectToSecure = sslEngine == null;
        }

        if (sslEngine != null) {
            HttpRedirector.redirectSSL(ctx,
                                       sslEngine,
                                       thread.getByteBuffer(),
                                       thread.getOutputBB(),
                                       redirectPort,
                                       redirectToSecure);
        } else {
            HttpRedirector.redirect(ctx,
                                    thread.getByteBuffer(),
                                    redirectPort,
                                    redirectToSecure);

        }

        // Skip the request remainder to avoid issues on the client side
        final SelectableChannel channel = ctx.getSelectionKey().channel();
        final ByteBuffer bb = thread.getByteBuffer();
        
        bb.clear();
        
        int totalBytesRead = 0;
        while(true) {
            Utils.Result result = null;
            try {
                result = Utils.readWithTemporarySelector(channel, bb, 20);
            } catch (Exception ignored) {
            }

            bb.clear();

            if (result == null || result.isClosed || result.bytesRead <= 0) {
                return true;
            }

            totalBytesRead += result.bytesRead;

            if (totalBytesRead > 4096) return true;
        }
    }


    public boolean postExecute(Context ctx) throws IOException {
        ctx.setKeyRegistrationState(Context.KeyRegistrationState.CANCEL);
        return true;
    }


    // ----------------------------------------- Methods from ConfigAwareElement
    /**
     * Configuration for &lt;http-redirect&gt;.
     *
     * @param configuration filter configuration
     */
    public void configure(Habitat habitat, ConfigBeanProxy configuration) {
        if (configuration instanceof HttpRedirect) {
            final HttpRedirect httpRedirectConfig = (HttpRedirect) configuration;
            int port = Integer.parseInt(httpRedirectConfig.getPort());
            redirectPort = port != -1 ? port : null;
            secure = Boolean.parseBoolean(httpRedirectConfig.getSecure());
        } else {
            // Retained for backwards compatibility with legacy redirect declarations.
        }
    }
}
