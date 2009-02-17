/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */
 

package com.sun.grizzly.standalone;

import com.sun.grizzly.http.SelectorThread;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import com.sun.grizzly.tcp.ActionCode;

import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;

/**
 * Simple HTTP based Web Server. Part of this class is from Tomcat sandbox code
 * from Costin Manolache.
 *
 * This class is deprecated and moved under 
 * http-utils/src/main/java/com/sun/grizzly/tcp/
 *
 * 
 * @author Jeanfrancois Arcand
 * @deprecated
 */
public class StaticResourcesAdapter implements Adapter {
       
    private String rootFolder = ".";
    
    private File rootFolderF;
    
    private ConcurrentHashMap<String,File> cache
            = new ConcurrentHashMap<String,File>();
       
    
    public StaticResourcesAdapter() {
        this(SelectorThread.getWebAppRootPath());
    }

    public StaticResourcesAdapter(String rootFolder) {


        this.rootFolder = rootFolder;
        rootFolderF = new File(rootFolder);
        try {
            rootFolder = rootFolderF.getCanonicalPath();
            SelectorThread.logger().log(Level.INFO, "New Servicing page from: " 
                + rootFolder);
        
        } catch (IOException e) {
        }
    }
    
    
    public void service(Request req, final Response res) throws Exception {
        MessageBytes mb = req.requestURI();
        ByteChunk requestURI = mb.getByteChunk();
        String uri = req.requestURI().toString();
        if (uri.indexOf("..") >= 0) {
            res.setStatus(404);
            return;
        }
        service(uri, req, res);
    }

    
    protected void service(String uri, Request req, final Response res)
        throws Exception {
     
        // local file
        File resource = cache.get(uri);
        if (resource == null){
            resource = new File(rootFolderF, uri);
            cache.put(uri,resource);
        }

        if (resource.isDirectory()) {
            resource = new File(resource, "index.html");
            cache.put(uri,resource);            
        }

        if (!resource.exists()) {
            SelectorThread.logger().log(Level.INFO,"File not found  " + resource);
            res.setStatus(404);
            return;
        }        
        res.setStatus(200);

        int dot=uri.lastIndexOf(".");
        if( dot > 0 ) {
            String ext=uri.substring(dot+1);
            String ct= MimeType.get(ext);
            if( ct!=null) {
                res.setContentType(ct);
            }
        } else {
            res.setContentType(MimeType.get("html"));
        }

        res.setContentLength((int)resource.length());        
        res.sendHeaders();

        /* Workaround Linux NIO bug
         * 6427312: (fc) FileChannel.transferTo() throws IOException "system call interrupted"
         * 5103988: (fc) FileChannel.transferTo should return -1 for EAGAIN instead throws IOException
         * 6253145: (fc) FileChannel.transferTo on Linux fails when going beyond 2GB boundary
         * 6470086: (fc) FileChannel.transferTo(2147483647, 1, channel) cause "Value too large" exception 
         */
        FileInputStream fis = new FileInputStream(resource);
        byte b[] = new byte[8192];
        ByteChunk chunk = new ByteChunk();
        int rd = 0;
        while ((rd = fis.read(b)) > 0) {
            chunk.setBytes(b, 0, rd);
            res.doWrite(chunk);
        }

        try{
            req.action( ActionCode.ACTION_POST_REQUEST , null);
        }catch (Throwable t) {
            t.printStackTrace();
        }
        
        res.finish();
    }
    
    
    public void afterService(Request req, Response res) 
        throws Exception {
        // Recycle the wrapper request and response
        req.recycle();
        res.recycle();     
    }

    
    public void fireAdapterEvent(String string, Object object) {
    }

    
    public String getRootFolder() {
        return rootFolder;
    }

    public void setRootFolder(String newRoot) {
        this.rootFolder = newRoot;
    }    
}