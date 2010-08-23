/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.comet;

import com.sun.grizzly.comet.concurrent.DefaultConcurrentCometHandler;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Gustav Trede
 */
public class CometTestAdapter extends GrizzlyAdapter {

    private String contextPath = "/comet/comet";

    private final boolean useConcurrentCometHandler;
    static CometContext cometContext;
    static volatile boolean usetreaming;

    public CometTestAdapter(String name, boolean useHandler, int idleTimeout) {
        cometContext = CometEngine.getEngine().register(name);
        cometContext.setBlockingNotification(false);
        cometContext.setExpirationDelay(idleTimeout);
        useConcurrentCometHandler = useHandler;
        // this.eventsperconnect = eventsperconnect;
        setHandleStaticResources(true);
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    @Override
    public void service(GrizzlyRequest req, GrizzlyResponse res) {
        CometHandler handler = useConcurrentCometHandler
                               ? new MyConcurrentCometHandler()
                               : new CometRequestHandler();
        try {
            handler.attach(res.getOutputStream());
            cometContext.addCometHandler(handler);
        } catch (IOException ex) {
            res.cancel();
        }
    }

    private void doEvent(OutputStream attachment, CometEvent event, CometHandler handler) throws IOException {
        if (event.getType() == CometEvent.NOTIFY) {
            Byte datat = (Byte) event.attachment();
            attachment.write(datat);
            attachment.flush();
            if (!usetreaming) {
                cometContext.resumeCometHandler(handler);
            }
        }
    }


    private class MyConcurrentCometHandler extends DefaultConcurrentCometHandler<OutputStream> {
        public void onEvent(CometEvent event) throws IOException {
            doEvent(attachment, event, this);
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


    private class CometRequestHandler implements CometHandler<OutputStream> {
        private OutputStream attachment;

        public void onEvent(CometEvent event) throws IOException {
            doEvent(attachment, event, this);
        }

        public void attach(OutputStream attachment) {
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
            try {
                //new Exception().printStackTrace();
                //if(usetreaming){
                attachment.close();
                // }
            }
            finally {
                //cometContext.removeCometHandler(this,false);
            }
        }
    }
}
