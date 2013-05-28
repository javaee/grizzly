/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
import java.net.MalformedURLException;
import java.net.URL;
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

/**
 * {@link HttpHandler}, which processes requests to a static resources resolved
 * by a given {@link ClassLoader}.
 *
 * @author Grizzly Team
 */
public class CLStaticHttpHandler extends StaticHttpHandlerBase {
    private static final Logger LOGGER = Grizzly.logger(CLStaticHttpHandler.class);

    private final ClassLoader classLoader;
    
    /**
     * Create <tt>HttpHandler</tt>, which will handle requests
     * to the static resources resolved by the given class loader.
     */
    public CLStaticHttpHandler(final ClassLoader classLoader) {
        if (classLoader == null) {
            throw new IllegalArgumentException("ClassLoader can not be null");
        }
        
        this.classLoader = classLoader;
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

        boolean found = false;
        File fileResource = null;
        
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }
        URL url = classLoader.getResource(resourcePath);
        
        if (url == null) {
            // try if resourcePath doesn't refer a folder in a jar
            url = classLoader.getResource(resourcePath + "/index.html");
            found = (url != null);
        } else {
            if ("file".equals(url.getProtocol())) {
                final File file = new File(url.toURI());
                
                if (file.exists()) {
                    if (file.isDirectory()) {
                        final File welcomeFile = new File(file, "/index.html");
                        if (welcomeFile.exists() && welcomeFile.isFile()) {
                            fileResource = welcomeFile;
                            found = true;
                        }
                    } else {
                        fileResource = file;
                        found = true;
                    }
                }
            } else {
                found = true;
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
        
        if (fileResource != null) {
            pickupContentType(response, fileResource.getPath());

            addToFileCache(request, response, fileResource);
            sendFile(response, fileResource);
        } else {
            pickupContentType(response, url.getPath());

            // if it's not a jar file - we don't know what to do with that
            // so not adding it to the file cache
            if ("jar".equals(url.getProtocol())) {
                final File jarFile = getJarFile(url.getPath());
                addTimeStampEntryToFileCache(request, response, jarFile);
            }
            
            sendResource(response, url.openStream());
        }

        return true;
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
        final int protocolIdx = path.indexOf(":");
        final int jarDelimIdx = path.indexOf("!/");
        if (protocolIdx == -1 || jarDelimIdx == -1) {
            throw new MalformedURLException("Either protocol or jar file delimeter were not found");
        }
        
        final File file = new File(path.substring(protocolIdx + 1, jarDelimIdx));
        
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("Jar file was not found");
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
}
