/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2012 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.util.DataStructures;
import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.http.HtmlHelper;
import com.sun.grizzly.util.http.HttpRequestURIDecoder;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.Exception;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.sun.grizzly.util.http.MimeType;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Simple {@link Adapter} that map the {@link Request} URI to a local file. The 
 * file is send synchronously using the NIO send file mechanism 
 * (@link File#transfertTo}.
 *
 * This class doesn't not decode the {@link Request} uri and just do 
 * basic security check. If you need more protection, use the {@link GrizzlyAdapter}
 * class instead or extend the {@link StaticResourcesAdapter#service}
 * and use {@link HttpRequestURIDecoder} to protect against security attack.
 *
 * @author Jeanfrancois Arcand
 */
public class StaticResourcesAdapter implements Adapter {

    private final static String USE_SEND_FILE =
            "com.sun.grizzly.useSendFile";
    private final Queue<String> rootFolders =
            DataStructures.getCLQinstance(String.class);
    protected String resourcesContextPath = "";
    protected final Queue<File> fileFolders =
            DataStructures.getCLQinstance(File.class);
    protected final ConcurrentHashMap<String, File> cache = new ConcurrentHashMap<String, File>();
    protected Logger logger = LoggerUtils.getLogger();
    private boolean useSendFile = true;
    /**
     * Commit the 404 response automatically.
     */
    protected boolean commitErrorResponse = true;
    private final ReentrantLock initializedLock = new ReentrantLock();
    private String defaultContentType = "text/html";

    public StaticResourcesAdapter() {
        this(".");
    }

    public StaticResourcesAdapter(String rootFolder) {
        addRootFolder(rootFolder);

        // Ugly workaround
        // See Issue 327
        if ((System.getProperty("os.name").equalsIgnoreCase("linux")
                && !linuxSendFileSupported())
                || System.getProperty("os.name").equalsIgnoreCase("HP-UX")) {
            useSendFile = false;
        }
        if (System.getProperty(USE_SEND_FILE) != null) {
            useSendFile = Boolean.valueOf(System.getProperty(USE_SEND_FILE));
            logger.info("Send-file enabled:" + useSendFile);
        }
    }

    /**
     * Based on the {@link Request} URI, try to map the file from the rootFolder, and send it synchronously using send file.
     * @param req the {@link Request} 
     * @param res the {@link Response} 
     * @throws Exception
     */
    public void service(Request req, final Response res) throws Exception {
        String uri = req.requestURI().toString();
        if (uri.contains("..") || !uri.startsWith(resourcesContextPath)) {
            res.setStatus(404);
            if (commitErrorResponse) {
                customizedErrorPage(req, res);
            }
            return;
        }

        // We map only file that take the form of name.extension
        if (uri.contains(".")) {
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
     * @throws Exception
     */
    protected void service(String uri, Request req, final Response res)
            throws Exception {
        FileInputStream fis = null;
        try {
            initWebDir();

            boolean found = false;
            File resource = null;

            for (File webDir : fileFolders) {
                // local file
                resource = cache.get(uri);
                if (resource == null) {
                    resource = new File(webDir, uri);
                    if (resource.exists() && resource.isDirectory()) {
                        final File f = new File(resource, "/index.html");
                        if (f.exists()) {
                            resource = f;
                            found = true;
                            break;
                        }
                    }
                }

                if (resource.isDirectory() || !resource.exists()) {
                    found = false;
                } else {
                    found = true;
                    break;
                }
            }

            cache.put(uri, resource);
            if (!found) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "File not found  " + resource);
                }
                res.setStatus(404);
                if (commitErrorResponse) {
                    customizedErrorPage(req, res);
                }
                return;
            }

            res.setStatus(200);
            String substr;
            int dot = uri.lastIndexOf(".");
            if (dot < 0) {
                substr = resource.toString();
                dot = substr.lastIndexOf(".");
            } else {
                substr = uri;
            }
            if (dot > 0) {
                String ext = substr.substring(dot + 1);
                String ct = MimeType.get(ext, defaultContentType);
                if (ct != null) {
                    res.setContentType(ct);
                }
            } else {
                res.setContentType(defaultContentType);
            }

            long length = resource.length();
            res.setContentLengthLong(length);

            // Send the header, and flush the bytes as we will now move to use
            // send file.
            res.sendHeaders();

            if (req.method().toString().equalsIgnoreCase("HEAD")){
                return;
            }

            fis = new FileInputStream(resource);
            OutputBuffer outputBuffer = res.getOutputBuffer();

            if (useSendFile &&
                    (outputBuffer instanceof FileOutputBuffer) &&
                    ((FileOutputBuffer) outputBuffer).isSupportFileSend()) {
                res.flush();

                long nWrite = 0;
                while (nWrite < length) {
                    nWrite += ((FileOutputBuffer) outputBuffer).sendFile(fis.getChannel(), nWrite, length - nWrite);
                }
            } else {
                byte b[] = new byte[8192];
                ByteChunk chunk = new ByteChunk();
                int rd;
                while ((rd = fis.read(b)) > 0) {
                    chunk.setBytes(b, 0, rd);
                    res.doWrite(chunk);
                }
            }
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Customize the error pahe 
     * @param req The {@link Request} object
     * @param res The {@link Response} object
     * @throws Exception
     */
    protected void customizedErrorPage(Request req,
            Response res) throws Exception {

        /**
         * With Grizzly, we just return a 404 with a simple error message.
         */
        res.setMessage("Not Found");
        res.setStatus(404);
        ByteBuffer bb = HtmlHelper.getErrorPage("Not Found", "HTTP/1.1 404 Not Found\r\n", "Grizzly");
        res.setContentLength(bb.limit());
        res.setContentType("text/html");
        res.flushHeaders();
        if (res.getChannel() != null) {
            res.getChannel().write(bb);
            req.setNote(14, "SkipAfterService");
        } else {
            byte b[] = new byte[bb.limit()];
            bb.get(b);
            ByteChunk chunk = new ByteChunk();
            chunk.setBytes(b, 0, b.length);
            res.doWrite(chunk);
        }
    }

    /**
     * Finish the {@link Response} and recycle the {@link Request} and the 
     * {@link Response}. If the {@link StaticResourcesAdapter#commitErrorResponse}
     * is set to false, this method does nothing.
     * 
     * @param req {@link Request}
     * @param res {@link Response}
     * @throws Exception
     */
    public void afterService(Request req, Response res) throws Exception {
        if (req.getNote(14) != null) {
            req.setNote(14, null);
            return;
        }

        if (res.getStatus() == 404 && !commitErrorResponse) {
            return;
        }

        try {
            req.action(ActionCode.ACTION_POST_REQUEST, null);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "afterService unexpected exception: ", t);
        }

        res.finish();

    }

    /**
     * Return the directory from where files will be serviced.
     * @return the directory from where file will be serviced.
     * @deprecated - use {@link #getRootFolders}
     */
    public String getRootFolder() {
        return rootFolders.peek();
    }

    /**
     * Set the directory from where files will be serviced.
     *
     * NOTE: For backward compatibility, invoking that method will
     * clear all previous values added using {@link #addRootFolder}.
     * 
     * @param rootFolder the directory from where files will be serviced.
     * 
     * @deprecated - use {@link #addRootFolder}
     */
    public void setRootFolder(String rootFolder) {
        rootFolders.clear();
        addRootFolder(rootFolder);
    }

    /**
     * Return the list of folders the adapter can serve file from.
     * @return a {@link Queue} of the folders this Adapter can
     * serve file from.
     */
    public Queue<String> getRootFolders() {
        return rootFolders;
    }

    /**
     * Add a folder to the list of folders this Adapter can serve file from.
     * @param rootFolder
     * @return
     */
    public boolean addRootFolder(String rootFolder) {
        return rootFolders.offer(rootFolder);
    }

    /**
     * Initialize.
     */
    protected void initWebDir() throws IOException {
        try {
            initializedLock.lock();
            if (fileFolders.isEmpty()) {
                for (String s : rootFolders) {
                    File webDir = new File(s);
                    fileFolders.offer(webDir);
                }
                rootFolders.clear();

                for (File f : fileFolders) {
                    rootFolders.add(f.getCanonicalPath());
                }
            }
        } finally {
            initializedLock.unlock();
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * @return <code>true</code> if {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}
     *  to send a static resources.
     */
    @SuppressWarnings("UnusedDeclaration")
    public boolean isUseSendFile() {
        return useSendFile;
    }

    /**
     * <code>true</code> if {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}
     * to send a static resources, false if the File needs to be loaded in
     * memory and flushed using {@link ByteBuffer}.
     *
     * @param useSendFile True if {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}
     *  to send a static resources, false if the File needs to be loaded in
     *  memory and flushed using {@link ByteBuffer}
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
    @SuppressWarnings("UnusedDeclaration")
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

    /**
     * If the content-type of the request cannot be determined, used the default
     * value. Current default is text/html
     * 
     * @return the defaultContentType
     */
    @SuppressWarnings("UnusedDeclaration")
    public String getDefaultContentType() {
        return defaultContentType;
    }

    /**
     * Set the default content-type if we can't determine it.
     * Default was text/html
     * 
     * @param defaultContentType the defaultContentType to set
     */
    public void setDefaultContentType(String defaultContentType) {
        this.defaultContentType = defaultContentType;
    }

    private static boolean linuxSendFileSupported(final String jdkVersion) {
        if (jdkVersion.startsWith("1.6")) {
            int idx = jdkVersion.indexOf('_');
            if (idx == -1) {
                return false;
            }
            StringBuilder sb = new StringBuilder(3);
            final String substr = jdkVersion.substring(idx + 1);
            int len = Math.min(substr.length(), 3);
            for (int i = 0; i < len; i++) {
                final char c = substr.charAt(i);
                if (Character.isDigit(c)) {
                    sb.append(c);
                    continue;
                }
                break;
            }
            if (sb.length() == 0) {
                return false;
            }
            final int patchRev = Integer.parseInt(sb.toString());
            return (patchRev >= 18);
        } else {
            return jdkVersion.startsWith("1.7") || jdkVersion.startsWith("1.8");
        }
    }

    private static boolean linuxSendFileSupported() {
        return linuxSendFileSupported(System.getProperty("java.version"));
    }
}
