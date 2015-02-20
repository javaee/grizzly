/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.memory.BufferArray;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.ArraySet;

/**
 * {@link HttpHandler}, which processes requests to a static resources resolved
 * by a given {@link ClassLoader}.
 *
 * @author Grizzly Team
 */
public class CLStaticHttpHandler extends StaticHttpHandlerBase {
    private static final Logger LOGGER = Grizzly.logger(CLStaticHttpHandler.class);
    
    protected static final String CHECK_NON_SLASH_TERMINATED_FOLDERS_PROP =
            CLStaticHttpHandler.class.getName() + ".check-non-slash-terminated-folders";
    
    /**
     * <tt>true</tt> (default) if we want to double-check the resource requests,
     * that don't have terminating slash if they represent a folder and try
     * to retrieve a welcome resource from the folder.
     */
    private static final boolean CHECK_NON_SLASH_TERMINATED_FOLDERS =
            System.getProperty(CHECK_NON_SLASH_TERMINATED_FOLDERS_PROP) == null ||
            Boolean.getBoolean(CHECK_NON_SLASH_TERMINATED_FOLDERS_PROP);
    
    private static final String SLASH_STR = "/";
    private static final String EMPTY_STR = "";

    private final ClassLoader classLoader;
    // path prefixes to be used
    private final ArraySet<String> docRoots = new ArraySet<String>(String.class);
    
    /**
     * Create <tt>HttpHandler</tt>, which will handle requests
     * to the static resources resolved by the given class loader.
     * @param classLoader {@link ClassLoader} to be used to resolve the resources
     * @param docRoots the doc roots (path prefixes), which will be used
     *          to find resources. Effectively each docRoot will be prepended
     *          to a resource path before passing it to {@link ClassLoader#getResource(java.lang.String)}.
     *          If no <tt>docRoots</tt> are set - the resources will be searched starting
     *          from {@link ClassLoader}'s root.
     * @throws IllegalArgumentException if one of the docRoots doesn't end with slash ('/')
     */
    public CLStaticHttpHandler(final ClassLoader classLoader,
            final String... docRoots) {
        if (classLoader == null) {
            throw new IllegalArgumentException("ClassLoader can not be null");
        }
        
        this.classLoader = classLoader;
        if (docRoots.length > 0) {
            for (String docRoot : docRoots) {
                if (!docRoot.endsWith("/")) {
                    throw new IllegalArgumentException("Doc root should end with slash ('/')");
                }
            }
            
            this.docRoots.addAll(docRoots);
        } else {
            this.docRoots.add("/");
        }
    }

    /**
     * Adds doc root (path prefix), which will be used to look up resources.
     * Effectively each registered docRoot will be prepended to a resource path
     * before passing it to {@link ClassLoader#getResource(java.lang.String)}.
     * 
     * @param docRoot
     * @return <tt>true</tt> if this docroot hasn't been registered before, or <tt>false</tt> otherwise.
     * 
     * @throws IllegalArgumentException if one of the docRoots doesn't end with slash ('/')
     */
    public boolean addDocRoot(final String docRoot) {
        if (!docRoot.endsWith("/")) {
            throw new IllegalArgumentException("Doc root should end with slash ('/')");
        }
        
        return docRoots.add(docRoot);
    }
    
    /**
     * Removes docRoot from the doc root list.
     * @param docRoot
     * @return <tt>true</tt> if this docroot was found and removed from the list, or
     *      or <tt>false</tt> if this docroot was not found in the list.
     */
    public boolean removeDocRoot(final String docRoot) {
        return docRoots.remove(docRoot);
    }
    
    /**
     * Returns the {@link ClassLoader} used to resolve the requested HTTP resources.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean handle(String resourcePath,
            final Request request,
            final Response response) throws Exception {

        URLConnection urlConnection = null;
        InputStream urlInputStream = null;
        
        if (resourcePath.startsWith(SLASH_STR)) {
            resourcePath = resourcePath.substring(1);
        }
        
        boolean mayBeFolder = true;
        
        if (resourcePath.length() == 0 || resourcePath.endsWith("/")) {
            resourcePath += "index.html";
            mayBeFolder = false;
        }
        
        URL url = lookupResource(resourcePath);
        
        if (url == null && mayBeFolder && CHECK_NON_SLASH_TERMINATED_FOLDERS) {
            // some ClassLoaders return null if a URL points to a folder.
            // So try to add index.html to double-check.
            // For example null will be returned for a folder inside a jar file.
            url = lookupResource(resourcePath + "/index.html");
            mayBeFolder = false;
        }
        
        File fileResource = null;
        String filePath = null;
        boolean found = false;
        
        if (url != null) {
            // url may point to a folder or a file
            if ("file".equals(url.getProtocol())) {
                final File file = new File(url.toURI());
                
                if (file.exists()) {
                    if (file.isDirectory()) {
                        final File welcomeFile = new File(file, "/index.html");
                        if (welcomeFile.exists() && welcomeFile.isFile()) {
                            fileResource = welcomeFile;
                            filePath = welcomeFile.getPath();
                            found = true;
                        }
                    } else {
                        fileResource = file;
                        filePath = file.getPath();
                        found = true;
                    }
                }
            } else {
                urlConnection = url.openConnection();
                if ("jar".equals(url.getProtocol())) {
                    final JarURLConnection jarUrlConnection = (JarURLConnection) urlConnection;
                    JarEntry jarEntry = jarUrlConnection.getJarEntry();
                    final JarFile jarFile = jarUrlConnection.getJarFile();
                    // check if this is not a folder
                    // we can't rely on jarEntry.isDirectory() because of http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6233323
                    InputStream is = null;
                    
                    if (jarEntry.isDirectory() ||
                            (is = jarFile.getInputStream(jarEntry)) == null) { // it's probably a folder
                        final String welcomeResource =
                                jarEntry.getName().endsWith("/") ?
                                jarEntry.getName() + "index.html" :
                                jarEntry.getName() + "/index.html";
                                
                        jarEntry = jarFile.getJarEntry(welcomeResource);
                        if (jarEntry != null) {
                            is = jarFile.getInputStream(jarEntry);
                        }
                    }
                    
                    if (is != null) {
                        urlInputStream = new JarURLInputStream(jarUrlConnection,
                                jarFile, is);
                        
                        assert jarEntry != null;
                        filePath = jarEntry.getName();
                        found = true;
                    } else {
                        closeJarFileIfNeeded(jarUrlConnection, jarFile);
                    }
                } else if ("bundle".equals(url.getProtocol())) { // OSGi resource
                    // it might be either folder or file
                    if (mayBeFolder &&
                            urlConnection.getContentLength() <= 0) { // looks like a folder?
                        // check if there's a welcome resource
                        final URL welcomeUrl = classLoader.getResource(url.getPath() + "/index.html");
                        if (welcomeUrl != null) {
                            url = welcomeUrl;
                            urlConnection = welcomeUrl.openConnection();
                        }
                    }
                    
                    found = true;
                } else {
                    found = true;
                }
            }
        }
       
        if (!found) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Resource not found {0}", resourcePath);
            }
            return false;
        }

        assert url != null;
        
        // If it's not HTTP GET - return method is not supported status
        if (!Method.GET.equals(request.getMethod())) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Resource found {0}, but HTTP method {1} is not allowed",
                        new Object[] {resourcePath, request.getMethod()});
            }
            response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
            response.setHeader(Header.Allow, "GET");
            return true;
        }
        
        pickupContentType(response,
                filePath != null ? filePath : url.getPath());
        
        if (fileResource != null) {
            addToFileCache(request, response, fileResource);
            sendFile(response, fileResource);
        } else {
            assert urlConnection != null;
            
            // if it's not a jar file - we don't know what to do with that
            // so not adding it to the file cache
            if ("jar".equals(url.getProtocol())) {
                final File jarFile = getJarFile(
                        // we need that because url.getPath() may have url encoded symbols,
                        // which are getting decoded when calling uri.getPath()
                        new URI(url.getPath()).getPath()
                );
                
                addTimeStampEntryToFileCache(request, response, jarFile);
            }
            
            sendResource(response,
                    urlInputStream != null ?
                    urlInputStream :
                    urlConnection.getInputStream());
        }

        return true;
    }

    private URL lookupResource(String resourcePath) {
        final String[] docRootsLocal = docRoots.getArray();
        if (docRootsLocal == null || docRootsLocal.length == 0) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "No doc roots registered -> resource {0} is not found ", resourcePath);
            }
            
            return null;
        }
        
        for (String docRoot : docRootsLocal) {
            if (SLASH_STR.equals(docRoot)) {
                docRoot = EMPTY_STR;
            } else if (docRoot.startsWith(SLASH_STR)) {
                docRoot = docRoot.substring(1);
            }
            
            final String fullPath = docRoot + resourcePath;
            final URL url = classLoader.getResource(fullPath);
            
            if (url != null) {
                return url;
            }
        }
        
        return null;
    }

    private static void sendResource(final Response response,
            final InputStream input) throws IOException {
        response.setStatus(HttpStatus.OK_200);

        response.addDateHeader(Header.Date, System.currentTimeMillis());
        final int chunkSize = 8192;
        
        response.suspend();
        
        final NIOOutputStream outputStream = response.getNIOOutputStream();
        
        outputStream.notifyCanWrite(
                new NonBlockingDownloadHandler(response, outputStream,
                        input, chunkSize));

    }

    private boolean addTimeStampEntryToFileCache(final Request req,
                                        final Response res,
                                        final File archive) {
        if (isFileCacheEnabled()) {
            final FilterChainContext fcContext = req.getContext();
            final FileCacheFilter fileCacheFilter = lookupFileCache(fcContext);
            if (fileCacheFilter != null) {
                final FileCache fileCache = fileCacheFilter.getFileCache();
                if (fileCache.isEnabled()) {
                    if (res != null) {
                        addCachingHeaders(res, archive);
                    }
                    fileCache.add(req.getRequest(), archive.lastModified());
                    return true;
                }
            }
        }

        return false;
    }
    
    private File getJarFile(final String path) throws MalformedURLException, FileNotFoundException {
        final int jarDelimIdx = path.indexOf("!/");
        if (jarDelimIdx == -1) {
            throw new MalformedURLException("The jar file delimeter were not found");
        }
        
        final File file = new File(path.substring(0, jarDelimIdx));
        
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("The jar file was not found");
        }
        
        return file;
    }
    
    private static class NonBlockingDownloadHandler implements WriteHandler {
        private final Response response;
        private final NIOOutputStream outputStream;
        private final InputStream inputStream;
        private final MemoryManager mm;
        private final int chunkSize;
        
        NonBlockingDownloadHandler(final Response response,
                final NIOOutputStream outputStream,
                final InputStream inputStream, final int chunkSize) {
            
            this.response = response;
            this.outputStream = outputStream;
            this.inputStream = inputStream;
            mm = response.getRequest().getContext().getMemoryManager();
            this.chunkSize = chunkSize;
        }
        
        @Override
        public void onWritePossible() throws Exception {
            LOGGER.log(Level.FINE, "[onWritePossible]");
            // send CHUNK of data
            final boolean isWriteMore = sendChunk();

            if (isWriteMore) {
                // if there are more bytes to be sent - reregister this WriteHandler
                outputStream.notifyCanWrite(this);
            }
        }

        @Override
        public void onError(Throwable t) {
            LOGGER.log(Level.FINE, "[onError] ", t);
            response.setStatus(500, t.getMessage());
            complete(true);
        }

        /**
         * Send next CHUNK_SIZE of file
         */
        private boolean sendChunk () throws IOException {
            // allocate Buffer
            Buffer buffer = null;
            
            if (!mm.willAllocateDirect(chunkSize)) {
                buffer = mm.allocate(chunkSize);
                final int len;
                if (!buffer.isComposite()) {
                    len = inputStream.read(buffer.array(),
                            buffer.position() + buffer.arrayOffset(),
                            chunkSize);
                } else {
                    final BufferArray bufferArray = buffer.toBufferArray();
                    final int size = bufferArray.size();
                    final Buffer[] buffers = bufferArray.getArray();

                    int lenCounter = 0;
                    for (int i = 0; i < size; i++) {
                        final Buffer subBuffer = buffers[i];
                        final int subBufferLen = subBuffer.remaining();
                        final int justReadLen = inputStream.read(subBuffer.array(),
                                subBuffer.position() + subBuffer.arrayOffset(),
                                subBufferLen);
                        
                        if (justReadLen > 0) {
                            lenCounter += justReadLen;
                        }
                        
                        if (justReadLen < subBufferLen) {
                            break;
                        }
                    }
                    
                    bufferArray.restore();
                    bufferArray.recycle();
                    
                    len = lenCounter > 0 ? lenCounter : -1;
                }
                    
                if (len > 0) {
                    buffer.position(buffer.position() + len);
                } else {
                    buffer.dispose();
                    buffer = null;
                }
            } else {
                final byte[] buf = new byte[chunkSize];
                final int len = inputStream.read(buf);
                if (len > 0) {
                    buffer = mm.allocate(len);
                    buffer.put(buf);
                }
            }
            
            if (buffer == null) {
                complete(false);
                return false;
            }
            // mark it available for disposal after content is written
            buffer.allowBufferDispose(true);
            buffer.trim();

            // write the Buffer
            outputStream.write(buffer);

            return true;
        }

        /**
         * Complete the download
         */
        private void complete(final boolean isError) {
            try {
                inputStream.close();
            } catch (IOException e) {
                if (!isError) {
                    response.setStatus(500, e.getMessage());
                }
            }

            try {
                outputStream.close();
            } catch (IOException e) {
                if (!isError) {
                    response.setStatus(500, e.getMessage());
                }
            }

            if (response.isSuspended()) {
                response.resume();
            } else {
                response.finish();
            }
        }
    }


    static class JarURLInputStream extends java.io.FilterInputStream {

        private final JarURLConnection jarConnection;
        private final JarFile jarFile;
        
        JarURLInputStream(final JarURLConnection jarConnection,
                final JarFile jarFile,
                final InputStream src) {
            super(src);
            this.jarConnection = jarConnection;
            this.jarFile = jarFile;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                closeJarFileIfNeeded(jarConnection, jarFile);
            }
        }
    }
    
    private static void closeJarFileIfNeeded(final JarURLConnection jarConnection,
            final JarFile jarFile) throws IOException {
        if (!jarConnection.getUseCaches()) {
            jarFile.close();
        }
    }
}
