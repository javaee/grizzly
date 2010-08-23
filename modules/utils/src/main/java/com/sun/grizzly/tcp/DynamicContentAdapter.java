/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.tcp;


import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.buf.ByteChunk;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.text.ParseException;

/**
 * Abstract Adapter that contains all the common behaviour of the Adapter implementation
 * for standalone usage as well as embedded use.
 *
 * @author Jerome Dochez
 * @author Jean-Francois Arcand
 * @deprecated - Use {@link GrizzlyAdapter} instead.
 */
abstract public class DynamicContentAdapter extends StaticResourcesAdapter {
    
    private final static int MAX_POST_SIZE = 48 * 1024;
    
    protected static final String RFC_2616_FORMAT = "EEE, d MMM yyyy HH:mm:ss z";
    
    
    protected String contextRoot = null;
    
    
    public DynamicContentAdapter() {
        super();
    }    
    
    public DynamicContentAdapter(String publicDirectory) {
        super(publicDirectory);
    }
    
    
    abstract protected int getTokenID();
    
    
    abstract protected void serviceDynamicContent(Request req, Response res) throws IOException;
    
    
    @Override
    public void afterService(Request req, Response res) throws Exception {
        ; // Let the Adapter handle the life cycle of the request/response.
    }
    
    
    private boolean modifiedSince(Request req, File file) {
        try {
            String since = req.getMimeHeaders().getHeader("If-Modified-Since");
            if (since == null) {
                return false;
            }
            
            Date date = new SimpleDateFormat(RFC_2616_FORMAT, Locale.US).parse(since);
            if (date.getTime() > file.lastModified()) {
                return true;
            } else {
                return false;
            }
        } catch (ParseException e) {
            return false;
        }
    }
    
    
    public void service(Request req, Response res) throws Exception {
        MessageBytes mb = req.requestURI();
        ByteChunk requestURI = mb.getByteChunk();
        
        try{
            String uri = requestURI.toString();
            if (contextRoot!=null && requestURI.startsWith(contextRoot)) {
                uri = uri.substring(contextRoot.length());
            }
            File file = new File(getRootFolder(),uri);
            if (file.isDirectory()) {
                uri += "index.html";
                file = new File(file,uri);
            }
            
            if (file.canRead()) {
                super.service(uri, req, res);
                return;
            } else {
                serviceDynamicContent(req, res);
            }
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, e.getMessage());
            }
            
            throw e;
        }
    }

    /**
     * Simple InputStream that wrap the Grizzly internal object.
     */
    private class GrizzlyInputStream extends InputStream {
        
        public RequestTupple rt;
        
        public int read() throws IOException {
            return rt.readChunk.substract();
        }
        
        public int read(byte[] b) throws IOException {
            return read(b,0,b.length);
        }
        
        public int read(byte[] b, int off, int len) throws IOException {
            return rt.readChunk.substract(b,off,len);
        }
    }
    
    
    /**
     * Statefull token used to share information with the Containers.
     */
    public class RequestTupple implements ByteChunk.ByteInputChannel{
        
        public ByteChunk readChunk;
        
        public Request req;
        
        public GrizzlyInputStream inputStream;
        
        public RequestTupple(){
            readChunk = new ByteChunk();
            readChunk.setByteInputChannel(this);
            readChunk.setBytes(new byte[MAX_POST_SIZE],0,MAX_POST_SIZE);
            
            inputStream = new GrizzlyInputStream();
            inputStream.rt = this;
        }
        
        public int realReadBytes(byte[] b, int off, int len) throws IOException {
            req.doRead(readChunk);
            return readChunk.substract(b,off,len);
        }
        
        public void recycle(){
            req = null;
            readChunk.recycle();
        }
        
    }
    
    public void setContextRoot(String contextRoot) {
        this.contextRoot = contextRoot;
    }
    
    
    public String getContextRoot() {
        return contextRoot;
    }
    
}
