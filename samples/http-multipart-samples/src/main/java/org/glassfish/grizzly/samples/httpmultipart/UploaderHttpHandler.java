/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.samples.httpmultipart;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.http.multipart.ContentDisposition;
import org.glassfish.grizzly.http.multipart.MultipartEntry;
import org.glassfish.grizzly.http.multipart.MultipartEntryHandler;
import org.glassfish.grizzly.http.multipart.MultipartScanner;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.io.NIOInputStream;

/**
 * The Grizzly {@link HttpHandler} implementation, which is responsible for
 * asynchronous and non-blocking uploading of the file specified in the
 * HTML form {@link FormHttpHandler}.
 * 
 * @author Alexey Stashok
 */
public class UploaderHttpHandler extends HttpHandler {
    private static final Logger LOGGER = Grizzly.logger(UploaderHttpHandler.class);

    private static final String DESCRIPTION_NAME = "description";
    private static final String FILENAME_ENTRY = "fileName";

    // uploads counter (just for debugging/tracking reasons)
    private final AtomicInteger uploadsCounter = new AtomicInteger(1);

    /**
     * Service HTTP request.
     */
    @Override
    public void service(final Request request, final Response response)
            throws Exception {
        
        // Suspend the HTTP request processing
        // (in other words switch to asynchronous HTTP processing mode).
        response.suspend();

        // assign uploadNumber for this specific upload
        final int uploadNumber = uploadsCounter.getAndIncrement();

        LOGGER.log(Level.INFO, "Starting upload #{0}", uploadNumber);

        // Initialize MultipartEntryHandler, responsible for handling
        // multipart entries of this request
        final UploaderMultipartHandler uploader =
                new UploaderMultipartHandler(uploadNumber);

        // Start the asynchronous multipart request scanning...
        MultipartScanner.scan(request,
                uploader,
                new EmptyCompletionHandler<Request>() {
            // CompletionHandler is called once HTTP request processing is completed
            // or failed.
            @Override
            public void completed(final Request request) {
                // Upload is complete
                final int bytesUploaded = uploader.getBytesUploaded();
                
                LOGGER.log(Level.INFO, "Upload #{0}: is complete. "
                        + "{1} bytes uploaded",
                        new Object[] {uploadNumber, bytesUploaded});

                // Compose a server response.
                try {
                    response.setContentType("text/plain");
                    final Writer writer = response.getWriter();
                    writer.write("Completed. " + bytesUploaded + " bytes uploaded.");
                } catch (IOException ignored) {
                }

                // Resume the asychronous HTTP request processing
                // (in other words finish the asynchronous HTTP request processing).
                response.resume();
            }

            @Override
            public void failed(Throwable throwable) {
                // if failed - log the error
                LOGGER.log(Level.INFO, "Upload #" + uploadNumber + " failed", throwable);
                // Complete the asynchronous HTTP request processing.
                response.resume();
            }


        });
    }

    /**
     * {@link MultipartEntryHandler}, responsible for processing the upload.
     */
    private final class UploaderMultipartHandler
            implements MultipartEntryHandler {
        
        // upload number
        private final int uploadNumber;
        // number of bytes uploaded
        private final AtomicInteger uploadedBytesCounter = new AtomicInteger();
        
        public UploaderMultipartHandler(final int uploadNumber) {
            this.uploadNumber = uploadNumber;
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public void handle(final MultipartEntry multipartEntry) throws Exception {
            // get the entry's Content-Disposition
            final ContentDisposition contentDisposition =
                    multipartEntry.getContentDisposition();
            // get the multipart entry name
            final String name = contentDisposition.getDispositionParamUnquoted("name");

            // if the multipart entry contains a file content
            if (FILENAME_ENTRY.equals(name)) {

                // get the filename for Content-Disposition
                final String filename =
                        contentDisposition.getDispositionParamUnquoted("filename");

                // Get the NIOInputStream to read the multipart entry content
                final NIOInputStream inputStream = multipartEntry.getNIOInputStream();

                LOGGER.log(Level.INFO, "Upload #{0}: uploading file {1}",
                        new Object[]{uploadNumber, filename});

                // start asynchronous non-blocking content read.
                inputStream.notifyAvailable(
                        new UploadReadHandler(uploadNumber, filename,
                        inputStream, uploadedBytesCounter));

            } else if (DESCRIPTION_NAME.equals(name)) { // if multipart entry contains a description field
                LOGGER.log(Level.INFO, "Upload #{0}: description came. "
                        + "Skipping...", uploadNumber);
                // skip the multipart entry
                multipartEntry.skip();
            } else { // Unexpected entry?
                LOGGER.log(Level.INFO, "Upload #{0}: unknown multipart entry. "
                        + "Skipping...", uploadNumber);
                // skip it
                multipartEntry.skip();
            }
        }

        /**
         * Returns the number of bytes uploaded for this multipart entry.
         * 
         * @return the number of bytes uploaded for this multipart entry.
         */
        int getBytesUploaded() {
            return uploadedBytesCounter.get();
        }
    }

    /**
     * Simple {@link ReadHandler} implementation, which is reading HTTP request
     * content (uploading file) in non-blocking mode and saves the content into
     * the specific file.
     */
    private static class UploadReadHandler implements ReadHandler {

        // the upload number
        private final int uploadNumber;
        // Non-blocking multipart entry input stream
        private final NIOInputStream inputStream;

        // the destination file output stream, where we save the data.
        private final FileOutputStream fileOutputStream;
        
        // temporary buffer
        private final byte[] buf;

        // uploaded bytes counter
        private final AtomicInteger uploadedBytesCounter;
        
        private UploadReadHandler(final int uploadNumber,
                final String filename,
                final NIOInputStream inputStream,
                final AtomicInteger uploadedBytesCounter)
                throws FileNotFoundException {

            this.uploadNumber = uploadNumber;
            fileOutputStream = new FileOutputStream(filename);
            this.inputStream = inputStream;
            this.uploadedBytesCounter = uploadedBytesCounter;
            buf = new byte[2048];
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onDataAvailable() throws Exception {
            // save available file content
            readAndSaveAvail();

            // register this handler to be notified next time some data
            // becomes available
            inputStream.notifyAvailable(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onAllDataRead() throws Exception {
            // save available file content
            readAndSaveAvail();
            // finish the upload
            finish();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onError(Throwable t) {
            LOGGER.log(Level.WARNING, "Upload #" + uploadNumber + ": failed", t);
            // finish the upload
            finish();
        }

        /**
         * Read available file content data out of HTTP request and save the
         * chunk into local file output stream
         * 
         * @throws IOException
         */
        private void readAndSaveAvail() throws IOException {
            while (inputStream.isReady()) {
                // read the available bytes from input stream
                final int readBytes = inputStream.read(buf);
                // update the counter
                uploadedBytesCounter.addAndGet(readBytes);
                // save the file content to the file
                fileOutputStream.write(buf, 0, readBytes);
            }
        }

        /**
         * Finish the file upload
         */
        private void finish() {
            try {
                // close file output stream
                fileOutputStream.close();
            } catch (IOException ignored) {
            }
        }
    }
}
