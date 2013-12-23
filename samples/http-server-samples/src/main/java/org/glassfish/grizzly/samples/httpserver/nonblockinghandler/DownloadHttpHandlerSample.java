/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.samples.httpserver.nonblockinghandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.util.MimeType;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * The sample shows how the HttpHandler should be implemented in order to
 * send large amount of data to a client in a non-blocking mode.
 * 
 * This sample is not intended to show the most optimal way to transfer file
 * data to a client. It supposed to demonstrate how to work with NIO output stream.
 * 
 * @author Alexey Stashok
 */
public class DownloadHttpHandlerSample {
    private static final Logger LOGGER = Grizzly.logger(DownloadHttpHandlerSample.class);

    private static final int CHUNK_SIZE = 8192;
    
    public static void main(String[] args) {

        // Read command line parameter - the parent directory
        if (args.length == 0) {
            System.out.println("Usage: DownloadHttpHandlerSample <download-folder>");
            System.exit(1);
        }

        // Check the parent directory
        final String downloadFolderName = args[0];

        final File downloadFolder = new File(downloadFolderName);
        if (!downloadFolder.isDirectory()) {
            System.out.println("The argument " + downloadFolderName + " is not a folder!");
            System.exit(1);
        }
        
        // create a basic server that listens on port 8080
        final NetworkListener networkListener = new NetworkListener("downloader",
                "0.0.0.0", 8080);
        
        // limit the max async write queue size per connection
        // usually we have to make the size big enough to accept data chunk + HTTP headers.
        networkListener.setMaxPendingBytes(CHUNK_SIZE * 4);
        
        final HttpServer server = new HttpServer();
        server.addListener(networkListener);
        
        final ServerConfiguration config = server.getServerConfiguration();

        // Map the NonBlockingUploadHandler to "/" URL
        config.addHttpHandler(new NonBlockingDownloadHandler(downloadFolder),
                "/");

        try {
            server.start();
            LOGGER.info("Press enter to stop the server...");
            System.in.read();
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE, ioe.toString(), ioe);
        } finally {
            server.shutdownNow();
        }
    }

    /**
     * This handler using non-blocking streams to write large amount of data to
     * a client.
     */
    private static class NonBlockingDownloadHandler extends HttpHandler {
        
        private final File parentFolder;

        public NonBlockingDownloadHandler(final File parentFolder) {
            this.parentFolder = parentFolder;
        }
        
        // -------------------------------------------- Methods from HttpHandler


        @Override
        public void service(final Request request,
                            final Response response) throws Exception {
            
            // Disable internal Response buffering
            response.setBufferSize(0);
            
            // put the stream in non-blocking mode
            final NIOOutputStream output = response.getNIOOutputStream();
            
            // get file path
            final String path = request.getDecodedRequestURI();
            
            final File file = new File(parentFolder, path);
            
            // check if file exists
            if (!file.isFile()) {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                return;
            }
            
            final FileChannel fileChannel =
                    new FileInputStream(file).getChannel();
            
            // set content-type
            final String contentType = MimeType.getByFilename(path);
            response.setContentType(contentType != null ? contentType : "binary/octet-stream");
            
            response.suspend();  // !!! suspend the Request

            // Notify the handler once we can write CHUNK_SIZE of data
            output.notifyCanWrite(new WriteHandler() {
                
                // keep the remaining size
                private long size = file.length();
                
                @Override
                public void onWritePossible() throws Exception {
                    LOGGER.log(Level.FINE, "[onWritePossible]");
                    // send CHUNK of data
                    final boolean isWriteMore = sendChunk();

                    if (isWriteMore) {
                        // if there are more bytes to be sent - reregister this WriteHandler
                        output.notifyCanWrite(this);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    LOGGER.log(Level.WARNING, "[onError] ", t);
                    response.setStatus(500, t.getMessage());
                    complete(true);
                }

                /**
                 * Send next CHUNK_SIZE of file
                 */
                private boolean sendChunk() throws IOException {
                    // allocate Buffer
                    final MemoryManager mm = request.getContext().getMemoryManager();
                    final Buffer buffer = mm.allocate(CHUNK_SIZE);
                    // mark it available for disposal after content is written
                    buffer.allowBufferDispose(true);
                                        
                    // read file to the Buffer
                    final int justReadBytes = fileChannel.read(buffer.toByteBuffer());
                    if (justReadBytes <= 0) {
                        complete(false);
                        return false;
                    }
                    
                    // prepare buffer to be written
                    buffer.position(justReadBytes);
                    buffer.trim();
                    
                    // write the Buffer
                    output.write(buffer);
                    size -= justReadBytes;
                    
                    // check the remaining size here to avoid extra onWritePossible() invocation
                    if (size <= 0) {
                        complete(false);
                        return false;
                    }
                    
                    return true;
                }

                /**
                 * Complete the download
                 */
                private void complete(final boolean isError) {
                    try {
                        fileChannel.close();
                    } catch (IOException e) {
                        if (!isError) {
                            response.setStatus(500, e.getMessage());
                        }
                    }
                    
                    try {
                        output.close();
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
            });
        }
    } // END NonBlockingDownloadHandler    
}
