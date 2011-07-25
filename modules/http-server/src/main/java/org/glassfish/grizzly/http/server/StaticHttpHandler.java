/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.glassfish.grizzly.http.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.server.io.OutputBuffer;
import org.glassfish.grizzly.http.server.util.MimeType;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
import org.glassfish.grizzly.utils.ArraySet;

/**
 * {@link HttpHandler}, which processes requests to a static resources.
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class StaticHttpHandler extends HttpHandler {
    private static final Logger LOGGER = Grizzly.logger(StaticHttpHandler.class);

    protected final ArraySet<File> docRoots = new ArraySet<File>(File.class);

    private volatile int fileCacheFilterIdx = -1;

    private boolean useSendFile;

    private Set<String> sendFileMimeTypes;


   /**
     * Create <tt>HttpHandler</tt>, which, by default, will handle requests
     * to the static resources located in the current directory.
     */
    public StaticHttpHandler() {
        addDocRoot(".");
    }


    /**
     * Create a new instance which will look for static pages located
     * under the <tt>docRoot</tt>. If the <tt>docRoot</tt> is <tt>null</tt> -
     * static pages won't be served by this <tt>HttpHandler</tt>
     *
     * @param docRoots the folder(s) where the static resource are located.
     * If the <tt>docRoot</tt> is <tt>null</tt> - static pages won't be served
     * by this <tt>HttpHandler</tt>
     */
    public StaticHttpHandler(String... docRoots) {
        if (docRoots != null) {
            for (String docRoot : docRoots) {
                addDocRoot(docRoot);
            }
        }
    }

    /**
     * Create a new instance which will look for static pages located
     * under the <tt>docRoot</tt>. If the <tt>docRoot</tt> is <tt>null</tt> -
     * static pages won't be served by this <tt>HttpHandler</tt>
     *
     * @param docRoots the folders where the static resource are located.
     * If the <tt>docRoot</tt> is empty - static pages won't be served
     * by this <tt>HttpHandler</tt>
     */
    public StaticHttpHandler(Set<String> docRoots) {
        if (docRoots != null) {
            for (String docRoot : docRoots) {
                addDocRoot(docRoot);
            }
        }
    }

    /**
     * Return the default directory from where files will be serviced.
     * @return the default directory from where file will be serviced.
     */
    public File getDefaultDocRoot() {
        final File[] array = docRoots.getArray();
        return (array != null && array.length > 0) ? array[0] : null;
    }

    /**
     * Return the list of directories where files will be serviced from.
     *
     * @return the list of directories where files will be serviced from.
     */
    public ArraySet<File> getDocRoots() {
        return docRoots;
    }

    /**
     * Add the directory to the list of directories where files will be serviced from.
     *
     * @param docRoot the directory to be added to the list of directories
     *                where files will be serviced from.
     *
     * @return return the {@link File} representation of the passed <code>docRoot</code>.
     */
    public final File addDocRoot(String docRoot) {
        if (docRoot == null) {
            throw new NullPointerException("docRoot can't be null");
        }

        final File file = new File(docRoot);
        addDocRoot(file);

        return file;
    }

    /**
     * Add the directory to the list of directories where files will be serviced from.
     *
     * @param docRoot the directory to be added to the list of directories
     *                where files will be serviced from.
     */
    public final void addDocRoot(File docRoot) {
        docRoots.add(docRoot);
    }

    /**
     * Removes the directory from the list of directories where static files will be serviced from.
     *
     * @param docRoot the directory to remove.
     */
    public void removeDocRoot(File docRoot) {
        docRoots.remove(docRoot);
    }

    /**
     * <p>
     * Returns <code>true</code> if static content will, when possible, be served
     * via {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}.
     * </p>
     *
     * @return <code>true</code> if static content will, when possible, be served
     * via {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}.
     *
     * @since 2.1.2
     */
    public boolean isUseSendFile() {
        return useSendFile;
    }

    /**
     * <p>
     * Configures whether or not static resources will be sent to the user-agent
     * via {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}.
     * </p>
     *
     * <p>
     * There are some restrictions on when {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}
     * can be used:
     * </p>
     *
     * <ul>
     *     <li>
     *         If the connection between user-agent/server is secure, this feature
     *         will not be used.
     *     </li>
     *     <li>
     *         If file caching is enabled on the {@link NetworkListener} to which
     *         the user-agent has connected, this feature will not be used.
     *     </li>
     * </ul>
     *
     * @param useSendFile if <code>true</code>, the {@link StaticHttpHandler} will
     *  attempt to use {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}
     *  where possible.
     *
     *  @since 2.1.2
     */
    public void setUseSendFile(boolean useSendFile) {
        this.useSendFile = useSendFile;
    }

    /**
     * <p>
     * Set the mime types that will be sent using send file.
     *
     * </p>
     *
     * @param sendFileMimeTypes a {@link Set} of mime types (i.e., zip, gzip, etc.)
     *
     * @since 2.1.2
     */
    public void setSendFileMimeTypes(Set<String> sendFileMimeTypes) {
        this.sendFileMimeTypes = sendFileMimeTypes;
    }

    /**
     * Based on the {@link Request} URI, try to map the file from the
     * {@link #getDocRoots()}, and send it back to a client.
     * @param request the {@link Request}
     * @param response the {@link Response}
     * @throws Exception
     */
    @Override
    public void service(final Request request, final Response response) throws Exception {
        final String uri = getRelativeURI(request);

        if (uri == null || !handle(uri, request, response)) {
            onMissingResource(request, response);
        }
    }

    protected String getRelativeURI(final Request request) {
        String uri = request.getRequestURI();
        if (uri.contains("..")) {
            return null;
        }

        final String resourcesContextPath = request.getContextPath();
        if (resourcesContextPath.length() > 0) {
            if (!uri.startsWith(resourcesContextPath)) {
                return null;
            }

            uri = uri.substring(resourcesContextPath.length());
        }

        return uri;
    }

    /**
     * The method will be called, if the static resource requested by the {@link Request}
     * wasn't found, so {@link StaticHttpHandler} implementation may try to
     * workaround this situation.
     * The default implementation - sends a 404 response page by calling {@link #customizedErrorPage(Request, Response)}.
     *
     * @param request the {@link Request}
     * @param response the {@link Response}
     * @throws Exception
     */
    protected void onMissingResource(final Request request, final Response response)
            throws Exception {
        response.setStatus(HttpStatus.NOT_FOUND_404);
        customizedErrorPage(request, response);
    }

    /**
     * Lookup a resource based on the request URI, and send it using send file.
     *
     * @param uri The request URI
     * @param req the {@link Request}
     * @param res the {@link Response}
     * @throws Exception
     */
    protected boolean handle(final String uri,
            final Request req,
            final Response res) throws Exception {

        boolean found = false;

        final File[] fileFolders = docRoots.getArray();
        if (fileFolders == null) {
            return false;
        }

        File resource = null;

        for (int i = 0; i < fileFolders.length; i++) {
            final File webDir = fileFolders[i];
            // local file
            resource = new File(webDir, uri);
            final boolean exists = resource.exists();
            final boolean readable = resource.canRead();
            final boolean isDirectory = resource.isDirectory();

            if (exists && isDirectory) {
                final File f = new File(resource, "/index.html");
                if (f.exists() && f.canRead()) {
                    resource = f;
                    found = true;
                    break;
                }
            }

            if (isDirectory || !exists || !readable) {
                found = false;
            } else {
                found = true;
                break;
            }
        }

        if (!found) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "File not found  {0}", resource);
            }
            return false;
        }

        // this call needs to happen *before* we attempt to cache the resource
        // as it depends on data from the Response object to properly setup
        // the cache entry.
        final String path = resource.getPath();
        final String mimeType = getMimeType(resource, path);
        prepareResponse(res, resource, mimeType, path);
        final boolean cached = addToFileCache(req, resource);
        sendFile(res, resource, canUseSendFile(req, mimeType, cached));

        return true;
    }

    private boolean canUseSendFile(Request req, String mimeType, boolean cached) {

        return (useSendFile
                  && !cached
                  && !req.isSecure()
                  && sendFileMimeTypes.contains(mimeType));

    }

    private static void sendFile(final Response response,
                                 final File file,
                                 final boolean useSendFile)
    throws IOException {

        final FileInputStream fis = new FileInputStream(file);

        try {
            final Connection c = response.ctx.getConnection();
            if (useSendFile && c instanceof TCPNIOConnection) {
                sendUsingTransferTo(response, file, fis, (TCPNIOConnection) c);
            } else {
                sendUsingBufferCopy(response, fis);
            }
        } finally {
            try {
                fis.close();
            } catch (IOException ignore) {
            }
        }
    }

    private static void prepareResponse(final Response response,
                                        final File file,
                                        final String mimeType,
                                        final String path) {
        response.setStatus(HttpStatus.OK_200);

        String type = MimeType.get(mimeType);
        if (type == null) {
            type = MimeType.get("html");
        }
        response.setContentType(type);
        final long length = file.length();
        response.setContentLengthLong(length);
    }

    public final boolean addToFileCache(Request req, File resource) {
        final FilterChainContext fcContext = req.getContext();
        final FileCacheFilter fileCacheFilter = lookupFileCache(fcContext);
        if (fileCacheFilter != null) {
            final FileCache fileCache = fileCacheFilter.getFileCache();
            if (fileCache.isEnabled()) {
                fileCache.add(req.getRequest(), resource);
                return true;
            }
        }

        return false;
    }

    private FileCacheFilter lookupFileCache(final FilterChainContext fcContext) {
        final FilterChain fc = fcContext.getFilterChain();
        final int lastFileCacheIdx = fileCacheFilterIdx;

        if (lastFileCacheIdx != -1) {
            final Filter filter = fc.get(lastFileCacheIdx);
            if (filter instanceof FileCacheFilter) {
                return (FileCacheFilter) filter;
            }
        }

        final int size = fc.size();
        for (int i = 0; i < size; i++) {
            final Filter filter = fc.get(i);

            if (filter instanceof FileCacheFilter) {
                fileCacheFilterIdx = i;
                return (FileCacheFilter) filter;
            }
        }

        fileCacheFilterIdx = -1;
        return null;
    }

    private static String getMimeType(final File file, final String path) {

        String substr;
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            substr = file.toString();
            dot = substr.lastIndexOf('.');
        } else {
            substr = path;
        }
        if (dot > 0) {
            String ext = substr.substring(dot + 1);
            return MimeType.get(ext);
        }
        return null;

    }

    private static void sendUsingTransferTo(final Response response,
                                            final File file,
                                            final FileInputStream fis,
                                            final TCPNIOConnection c)
    throws IOException {

        // Flush the headers to the client.  Once complete, *then(
        // invoke FileChannel.transferTo() directly against
        // the connection's channel.
        response.suspend();
        response.flush(new CompletionHandler<WriteResult>() {
            private AtomicBoolean cancelled = new AtomicBoolean(false);
            @Override
            public void cancelled() {
                cancelled.compareAndSet(false, true);
            }

            @Override
            public void failed(Throwable throwable) {
                logAndClose(throwable);
            }

            @Override
            public void completed(WriteResult result) {
                final FileChannel fileChannel = fis.getChannel();
                final SelectableChannel sc = c.getChannel();

                try {
                    final long origPos = fileChannel.position();
                    final long totalLen = file.length();
                    long movingLen = totalLen;
                    long written = 0;
                    final WritableByteChannel writeChannel =
                            (WritableByteChannel) sc;
                    while (written < totalLen && !cancelled.get()) {
                        final long thisWrite =
                                fileChannel.transferTo(origPos + written,
                                                       movingLen,
                                                       writeChannel);
                        written += thisWrite;
                        movingLen -= thisWrite;
                    }
                } catch (IOException ioe) {
                    logAndClose(ioe);
                } finally {
                    response.resume();
                }
            }

            @Override
            public void updated(WriteResult result) {
                // no-op
            }

            private void logAndClose(final Throwable t) {
                final boolean clientDisconnect =
                        t.getMessage().contains("Broken Pipe");
                if (!clientDisconnect) {
                    if (LOGGER.isLoggable(Level.SEVERE)) {
                        LOGGER.log(Level.SEVERE, t.toString(), t);
                    }
                } else {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, t.toString(), t);
                    }
                }
                try {
                    c.close().markForRecycle(true);
                } catch (IOException ignored) {
                }
            }

        });
    }

    private static void sendUsingBufferCopy(final Response response,
                                            final FileInputStream fis)
    throws IOException {

        final OutputBuffer outputBuffer = response.getOutputBuffer();

        byte b[] = new byte[8192];
        int rd;
        while ((rd = fis.read(b)) > 0) {
            //chunk.setBytes(b, 0, rd);
            outputBuffer.write(b, 0, rd);
        }

    }
    
}
