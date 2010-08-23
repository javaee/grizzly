/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.container;

import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEngine;
import com.sun.grizzly.grizzlet.Grizzlet;
import com.sun.grizzly.tcp.ActionCode;
import java.io.File;

import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.ClassLoaderUtil;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Standalone Comet implementation. This class is used when Comet is enabled
 * from the Grizzly standalone WebServer. This class is responsible for invoking
 * the Grizzlet defined when starting the Grizzly WebServer.
 *
 * @author Jeanfrancois Arcand
 */
public class GrizzletAdapter extends StaticResourcesAdapter implements Adapter {

    public static final int ADAPTER_NOTES = 5;
    public static final int POST = 6312;
    public static final int GRIZZLET = 6;
    /**
     * All request to that context-path will be considered as comet enabled.
     */
    private String cometContextName = "/grizzlet";
    private ReentrantLock initializedLock = new ReentrantLock();
    /**
     * The Grizzlet associated with this Adapter.
     */
    private Grizzlet grizzlet;
    private String grizzletName;

    public GrizzletAdapter() {
        super();
    }

    public GrizzletAdapter(String cometContextName) {
        super();
        this.cometContextName = cometContextName;
    }

    /**
     * Route the request to the comet implementation. If the request point to
     * a static file, delegate the call to the Grizzly WebServer implementation.
     */
    @Override
    public void service(Request req, final Response res) throws Exception {
        String uri = req.requestURI().toString();
        File file = new File(getRootFolder(), uri);
        if (file.isDirectory()) {
            uri += "index.html";
            file = new File(file, uri);
        }

        if (file.canRead()) {
            super.service(req, res);
            return;
        }

        CometEngine cometEngine = CometEngine.getEngine();
        CometContext cometContext = cometEngine.getCometContext(cometContextName);

        initializedLock.lock();
        try {
            if (cometContext == null) {
                cometContext = cometEngine.register(cometContextName);
                cometContext.setExpirationDelay(-1);
                cometContext.setBlockingNotification(true);

            }

            if (grizzlet == null && grizzletName != null) {
                grizzlet = (Grizzlet) ClassLoaderUtil.load(grizzletName);
            }
        } finally {
            initializedLock.unlock();
        }

        GrizzletRequest cometReq = (GrizzletRequest) req.getNote(ADAPTER_NOTES);
        GrizzletResponse cometRes = (GrizzletResponse) res.getNote(ADAPTER_NOTES);
        AsyncConnectionImpl asyncConnection =
                (AsyncConnectionImpl) req.getNote(GRIZZLET);

        if (cometReq == null) {
            cometReq = new GrizzletRequest(req);
            cometRes = new GrizzletResponse(res);
            cometReq.setGrizzletResponse(cometRes);
            asyncConnection = new AsyncConnectionImpl();

            // Set as notes so we don't create them on every request.
            req.setNote(ADAPTER_NOTES, cometReq);
            req.setNote(GRIZZLET, asyncConnection);
            res.setNote(ADAPTER_NOTES, cometRes);
        } else {
            cometReq.setRequest(req);
            cometRes.setResponse(res);
        }
        asyncConnection.setCometContext(cometContext);
        asyncConnection.setRequest(cometReq);
        asyncConnection.setResponse(cometRes);
        asyncConnection.setGrizzlet(grizzlet);

        try {
            synchronized (grizzlet){
                grizzlet.onRequest(asyncConnection);
            }
        } finally {
            cometReq.recycle();
            cometRes.recycle();
            asyncConnection.recycle();
        }
    }

    @Override
    public void afterService(Request req, Response res)
            throws Exception {
        try {
            req.action(ActionCode.ACTION_POST_REQUEST, null);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        res.finish();
        super.afterService(req, res);
    }

    /**
     * Set the user defined {@link Grizzlet} implementation.
     */
    public Grizzlet getGrizzlet() {
        return grizzlet;
    }

    /**
     * Return the user defined {@link Grizzlet} implementation. 
     */
    public void setGrizzlet(Grizzlet grizzlet) {
        this.grizzlet = grizzlet;
    }

    public String getCometContextName() {
        return cometContextName;
    }

    public void setCometContextName(String cometContextName) {
        this.cometContextName = cometContextName;
    }

    /**
     * Set the user defined {@link Grizzlet} implementation.
     */
    public String getGrizzletName() {
        return grizzletName;
    }

    /**
     * Return the user defined {@link Grizzlet} implementation.
     */
    public void setGrizzletName(String grizzletName) {
        this.grizzletName = grizzletName;
    }
}
