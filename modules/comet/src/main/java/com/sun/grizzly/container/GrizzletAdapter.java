
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
 *
 * Portions Copyright Apache Software Foundation.
 */

package com.sun.grizzly.container;


import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEngine;
import com.sun.grizzly.grizzlet.Grizzlet;
import java.io.File;

import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Standalone Comet implementation. This class is used when Comet is enabled
 * from the Grizzly standalone WebServer. This class is responsible for invoking
 * the Grizzlet defined when starting the Grizzly WebServer.
 *
 * @author Jeanfrancois Arcand
 */
public class GrizzletAdapter extends StaticResourcesAdapter implements Adapter {
        
    public static final int ADAPTER_NOTES = 1;    
    public static final int POST = 6312;    
    public static final int GRIZZLET = 2;    
    
    
    /**
     * All request to that context-path will be considered as comet enabled.
     */
    private String cometContextName = "/comet";      

    
    private ReentrantLock initializedLock = new ReentrantLock();    
    
    /**
     * The Grizzlet associated with this Adapter.
     */
    private Grizzlet grizzlet;

    
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
        MessageBytes mb = req.requestURI();
        ByteChunk requestURI = mb.getByteChunk();
        String uri = req.requestURI().toString();
        File file = new File(getRootFolder(),uri);
        if (file.isDirectory()) {
            uri += "index.html";
            file = new File(file,uri);
        }

        if (file.canRead()) {
            super.service(req,res);
            return;
        }
             
        CometEngine cometEngine = CometEngine.getEngine();
        CometContext cometContext = cometEngine.getCometContext(cometContextName);  
                
        initializedLock.lock();
        try{        
            if (cometContext == null){
                cometContext = cometEngine.register(cometContextName);
                cometContext.setExpirationDelay(-1);    
                cometContext.setBlockingNotification(true);        
            }
        } finally{
             initializedLock.unlock();
        }

        GrizzletRequest cometReq = (GrizzletRequest) req.getNote(ADAPTER_NOTES);
        GrizzletResponse cometRes = (GrizzletResponse) res.getNote(ADAPTER_NOTES);
        AsyncConnectionImpl asyncConnection = 
                (AsyncConnectionImpl) req.getNote(GRIZZLET);
        
        if (cometReq == null) {
            cometReq = new GrizzletRequest(req);            
            cometRes = new GrizzletResponse(res);
            cometReq.setResponse(cometRes);
            asyncConnection = new AsyncConnectionImpl();
            
            // Set as notes so we don't create them on every request.'
            req.setNote(ADAPTER_NOTES, cometReq);
            req.setNote(GRIZZLET, asyncConnection);
            res.setNote(ADAPTER_NOTES, cometRes);
        }  else {
            cometReq.setRequest(req);
            cometRes.setResponse(res);
        }
        asyncConnection.setCometContext(cometContext);
        asyncConnection.setRequest(cometReq);
        asyncConnection.setResponse(cometRes);
        asyncConnection.setGrizzlet(grizzlet);

        grizzlet.onRequest(asyncConnection); 
        asyncConnection.recycle();
    }   

    
    /**
     * Set the user defined <code>Grizzlet</code> implementation.
     */
    public Grizzlet getGrizzlet() {
        return grizzlet;
    }

    
    /**
     * Return the user defined <code>Grizzlet</code> implementation. 
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
}