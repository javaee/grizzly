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


package com.sun.grizzly.tcp;

import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.http.HtmlHelper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.sun.grizzly.util.http.MimeType;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Simple {@link Adapter} that map the {@link Request} URI to a local file. The 
 * file is send synchronously using the NIO send file mechanism 
 * (@link File#transfertTo}.
 * 
 * This class doesn't not decode the {@link Request} uri and just do 
 * basic security check. If you need more protection, use the {@link GrizzlyAdapter}
 * class instead or extend the {@link StaticResourcesAdapter#service()}
 * and use {@link HttpRequestURIDecoder} to protect against security attack.
 *
 * @author Jeanfrancois Arcand
 */
public class StaticResourcesAdapter implements Adapter {
    
    private final static String USE_SEND_FILE =
        "com.sun.grizzly.useSendFile";    
    
    
    protected String rootFolder = ".";
    
    protected String resourcesContextPath = "";

    protected File webDir = null;

    protected ConcurrentHashMap<String,File> cache
        = new ConcurrentHashMap<String,File>();

    protected Logger logger = LoggerUtils.getLogger();

    private boolean useSendFile = true;

    /**
     * Commit the 404 response automatically.
     */
    protected boolean commitErrorResponse = true;
    
    private ReentrantLock initializedLock = new ReentrantLock();


    public StaticResourcesAdapter() {
        this(".");
    }


    public StaticResourcesAdapter(String rootFolder) {
        this.rootFolder = rootFolder;
        
        // Ugly workaround
        // See Issue 327
        if (System.getProperty("os.name").equalsIgnoreCase("linux") 
                && !System.getProperty("java.version").startsWith("1.7")) {
            useSendFile = false;
        } 
        
        if (System.getProperty(USE_SEND_FILE)!= null){
            useSendFile = Boolean.valueOf(System.getProperty(USE_SEND_FILE)).booleanValue();
            logger.info("Send-file enabled:" + useSendFile);
        }   
    }


    /** 
     * Based on the {@link Request} URI, try to map the file from the 
     * {@link StaticResourcesAdapter#rootFolder}, and send it synchronously using send file.
     * @param req the {@link Request} 
     * @param res the {@link Response} 
     * @throws java.lang.Exception
     */
    public void service(Request req, final Response res) throws Exception {
        String uri = req.requestURI().toString();
        if (uri.indexOf("..") >= 0 || !uri.startsWith(resourcesContextPath)) {
            res.setStatus(404);
            if (commitErrorResponse){
                customizedErrorPage(req,res);
            }
            return;        
        }
             
        // We map only file that take the form of name.extension
        if (uri.indexOf(".") != -1){
            uri = uri.substring(resourcesContextPath.length());
        }
        
        service(uri, req, res);
    }


    /**
     * Lookup a resource based on the request URI, and send it using send file. 
     * 
     * @param uri The request URI
     * @param req the {@link Request}
     * @param res the {@link Response}
     * @throws java.lang.Exception
     */
    protected void service(String uri, Request req, final Response res)
        throws Exception {
        FileInputStream fis = null;
        try{
            initWebDir();

            // local file
            File resource = cache.get(uri);
            if (resource == null){
                resource = new File(webDir, uri);
                cache.put(uri,resource);
            }

            if (resource.isDirectory()) {
                req.action( ActionCode.ACTION_REQ_LOCAL_ADDR_ATTRIBUTE , null);
                res.setStatus(302);
                res.setMessage("Moved Temporarily");                
                res.setHeader("Location", req.scheme() + "://" 
                        + req.serverName() + ":" + req.getServerPort() 
                        + "/index.html");
                res.setHeader("Connection", "close");
                res.setHeader("Cache-control", "private");
                res.sendHeaders();
                return;    
            }

            if (!resource.exists()) {
                if (logger.isLoggable(Level.FINE)){
                    logger.log(Level.FINE,"File not found  " + resource);
                }
                res.setStatus(404);
                if (commitErrorResponse){
                    customizedErrorPage(req,res);
                }
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

            long length = resource.length();
            res.setContentLengthLong(length);    

            // Send the header, and flush the bytes as we will now move to use
            // send file.
            res.sendHeaders();

            fis = new FileInputStream(resource);
            OutputBuffer outputBuffer = res.getOutputBuffer();

            if (useSendFile &&
                    (outputBuffer instanceof FileOutputBuffer) &&
                    ((FileOutputBuffer) outputBuffer).isSupportFileSend()) {
                res.flush();
         
                long nWrite = 0;
                while (nWrite < length) {
                    nWrite += ((FileOutputBuffer) outputBuffer).
                            sendFile(fis.getChannel(), nWrite, length - nWrite);
                }    
            } else {
                byte b[] = new byte[8192];
                ByteChunk chunk = new ByteChunk();
                int rd = 0;
                while ((rd = fis.read(b)) > 0) {
                    chunk.setBytes(b, 0, rd);
                    res.doWrite(chunk);
                }   
            }
        } finally {
            if (fis != null){
                try{
                    fis.close();
                } catch (IOException ex){}
            }
        }
    }    


    /**
     * Customize the error pahe 
     * @param req The {@link Request} object
     * @param res The {@link Response} object
     * @throws java.lang.Exception
     */
    protected void customizedErrorPage(Request req,
            Response res) throws Exception {        
        
        /**
         * With Grizzly, we just return a 404 with a simple error message.
         */ 
        res.setMessage("Not Found");
        ByteBuffer bb = HtmlHelper.getErrorPage("Not Found","HTTP/1.1 404 Not Found\r\n", "Grizzly");
        res.setContentLength(bb.limit());
        res.setContentType("text/html");
        res.flushHeaders();        
        res.getChannel().write(bb);
        req.setNote(14, "SkipAfterService");
    }


    /**
     * Finish the {@link Response} and recycle the {@link Request} and the 
     * {@link Response}. If the {@link StaticResourcesAdapter#commitErrorResponse}
     * is set to false, this method does nothing.
     * 
     * @param req {@link Request}
     * @param res {@link Response}
     * @throws java.lang.Exception
     */
    public void afterService(Request req, Response res) throws Exception {
        if (req.getNote(14) != null){
            req.setNote(14, null);
            return;
        }
                
        if (res.getStatus() == 404 && !commitErrorResponse){
            return;
        }

        try{
            req.action( ActionCode.ACTION_POST_REQUEST , null);
        }catch (Throwable t) {
            logger.log(Level.WARNING,"afterService unexpected exception: ",t);
        }

        res.finish();
        req.recycle();
        res.recycle();     
    }


    /**
     * Return the directory from where files will be serviced.
     * @return the directory from where file will be serviced.
     */
    public String getRootFolder() {
        return rootFolder;
    }


    /**
     * Set the directory from where files will be serviced.
     * @param rootFolder the directory from where files will be serviced.
     */
    public void setRootFolder(String rootFolder) {
        this.rootFolder = rootFolder;
    }


    /**
     * Initialize.
     */
    protected void initWebDir(){
        if (webDir == null){         
            try{
                initializedLock.lock();
                webDir = new File(rootFolder);
                try {
                    rootFolder = webDir.getCanonicalPath();
                } catch (IOException e) {
                    logger.log(Level.WARNING,"service()",e);
                }
            } finally {
                initializedLock.unlock();
            }
        }
    }
    
    
    public void setLogger(Logger logger) {
        this.logger = logger;
    }
    
    
    /**
     * True if {@link File#transfertTo} to send a static resources.
     * @return True if {@link File#transfertTo} to send a static resources.
     */
    public boolean isUseSendFile() {
        return useSendFile;
    }

    
    /**
     * True if {@link File#transfertTo} to send a static resources, false if
     * the File needs to be loaded in memory and flushed using {@link ByteBuffer}
     * @param useSendFile True if {@link File#transfertTo} to send a static resources, false if
     * the File needs to be loaded in memory and flushed using {@link ByteBuffer}
     */
    public void setUseSendFile(boolean useSendFile) {
        this.useSendFile = useSendFile;
    }

    /**
     * Return the context path used for servicing resources. By default, "" is
     * used so request taking the form of http://host:port/index.html are serviced
     * directly. If set, the resource will be available under 
     * http://host:port/context-path/index.html
     * @return the context path.
     */
    public String getResourcesContextPath() {
        return resourcesContextPath;
    }

    /**
     * Set the context path used for servicing resource. By default, "" is
     * used so request taking the form of http://host:port/index.html are serviced
     * directly. If set, the resource will be available under 
     * http://host:port/context-path/index.html
     * @param resourcesContextPath the context path
     */
    public void setResourcesContextPath(String resourcesContextPath) {
        this.resourcesContextPath = resourcesContextPath;
    }
}
