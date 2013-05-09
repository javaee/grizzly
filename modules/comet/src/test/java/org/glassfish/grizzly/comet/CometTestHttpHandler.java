/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.comet;

import java.io.IOException;

import org.glassfish.grizzly.comet.concurrent.DefaultConcurrentCometHandler;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.io.NIOOutputStream;

/**
 * @author Gustav Trede
 */
public class CometTestHttpHandler extends HttpHandler {
    private final boolean useConcurrentCometHandler;
    static CometContext<Byte> cometContext;
    static volatile boolean useStreaming;

    public CometTestHttpHandler(String name, boolean useHandler, int idleTimeout) {
        cometContext = CometEngine.getEngine().<Byte>register(name);
        cometContext.setExpirationDelay(idleTimeout);
        useConcurrentCometHandler = useHandler;
        // this.eventsperconnect = eventsperconnect;
    }

    @Override
    public void service(Request req, Response res) {
        cometContext.addCometHandler(useConcurrentCometHandler
                               ? new MyConcurrentCometHandler(cometContext, res)
                               : new CometRequestHandler());
    }

    private void doEvent(CometEvent event, CometHandler handler) throws IOException {
        if (event.getType() == CometEvent.Type.NOTIFY) {
            final NIOOutputStream outputStream = handler.getResponse().getNIOOutputStream();
            outputStream.write((Byte) event.attachment());
            outputStream.flush();
            if (!useStreaming) {
                cometContext.resumeCometHandler(handler);
            }
        }
    }

    private class MyConcurrentCometHandler extends DefaultConcurrentCometHandler<Byte> {

        private MyConcurrentCometHandler(CometContext<Byte> context, Response response) {
            super(context, response);
        }

        public void onEvent(CometEvent event) throws IOException {
            doEvent(event, this);
        }

        public void onInitialize(CometEvent arg0) throws IOException {
        }

        public void onInterrupt(CometEvent event) throws IOException {
            // new Exception().printStackTrace();
            super.onInterrupt(event);
        }

        public void onTerminate(CometEvent event) throws IOException {
            //new Exception().printStackTrace();
            super.onTerminate(event);
        }
    }

    private class CometRequestHandler implements CometHandler<Byte> {
        private Byte attachment;
        private CometContext<Byte> context;
        private Response response;

        public void onEvent(CometEvent event) throws IOException {
            doEvent(event, this);
        }

        public void attach(Byte attachment) {
            this.attachment = attachment;
        }

        public void onInitialize(CometEvent event) throws IOException {
        }

        public void onInterrupt(CometEvent event) throws IOException {
            doClose();
        }

        public void onTerminate(CometEvent event) throws IOException {
            doClose();
        }

        private void doClose() throws IOException {
            getResponse().finish();
        }

        @Override
        public CometContext<Byte> getCometContext() {
            return context;
        }

        @Override
        public void setCometContext(final CometContext<Byte> context) {
            this.context = context;
        }

        @Override
        public void setResponse(final Response response) {
            this.response = response;
        }

        @Override
        public Response getResponse() {
            return response;
        }
    }
}
