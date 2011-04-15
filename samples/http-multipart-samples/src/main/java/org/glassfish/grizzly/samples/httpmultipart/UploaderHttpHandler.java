/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.http.multipart.ContentDisposition;
import org.glassfish.grizzly.http.multipart.MultipartEntry;
import org.glassfish.grizzly.http.multipart.MultipartEntryHandler;
import org.glassfish.grizzly.http.multipart.MultipartScanner;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.io.NIOInputStream;
import org.glassfish.grizzly.http.server.io.ReadHandler;

/**
 *
 * @author oleksiys
 */
public class UploaderHttpHandler extends HttpHandler {
    private static final Logger LOGGER = Grizzly.logger(UploaderHttpHandler.class);

    private static final String DESCRIPTION_NAME = "description";
    private static final String FILENAME_ENTRY = "fileName";

    private final AtomicInteger uploadsCounter = new AtomicInteger(1);


    @Override
    public void service(final Request request, final Response response)
            throws Exception {
        
        response.suspend();

        final int uploadNumber = uploadsCounter.getAndIncrement();

        LOGGER.log(Level.INFO, "Starting upload #{0}", uploadNumber);

        final UploaderMultipartHandler uploader =
                new UploaderMultipartHandler(uploadNumber);

        MultipartScanner.scan(request,
                uploader,
                new EmptyCompletionHandler<Request>() {

            @Override
            public void completed(Request request) {
                final int bytesUploaded = uploader.getBytesUploaded();
                
                LOGGER.log(Level.INFO, "Upload #{0}: is complete. "
                        + "{1} bytes uploaded",
                        new Object[] {uploadNumber, bytesUploaded});

                try {
                    response.setContentType("text/plain");
                    final Writer writer = response.getWriter();
                    writer.write("Completed. " + bytesUploaded + " bytes uploaded.");
                } catch (IOException ignored) {
                }

                response.finish();
                response.resume();
            }

            @Override
            public void failed(Throwable throwable) {
                LOGGER.log(Level.INFO, "Upload #{0} failed", uploadNumber);
                response.resume();
            }


        });
    }

    private class UploaderMultipartHandler extends MultipartEntryHandler {
        private final int uploadNumber;
        private final AtomicInteger uploadedBytesCounter = new AtomicInteger();
        
        public UploaderMultipartHandler(int uploadNumber) {
            this.uploadNumber = uploadNumber;
        }
        
        @Override
        public void handle(final MultipartEntry part) throws Exception {
            final ContentDisposition contentDisposition = part.getContentDisposition();
            final String name = contentDisposition.getDispositionParamUnquoted("name");
            if (FILENAME_ENTRY.equals(name)) {

                final String filename =
                        contentDisposition.getDispositionParamUnquoted("filename");

                final NIOInputStream inputStream = part.getNIOInputStream();

                inputStream.notifyAvailable(
                        new UploadReadHandler(uploadNumber, filename,
                        inputStream, uploadedBytesCounter));

                LOGGER.log(Level.INFO, "Upload #{0}: uploading file {1}",
                        new Object[]{uploadNumber, filename});

            } else if (DESCRIPTION_NAME.equals(name)) {
                LOGGER.log(Level.INFO, "Upload #{0}: description came. "
                        + "Skipping...", uploadNumber);
                part.skip();
            } else {
                LOGGER.log(Level.INFO, "Upload #{0}: unknown multipart entry. "
                        + "Skipping...", uploadNumber);
                part.skip();
            }
        }

        private int getBytesUploaded() {
            return uploadedBytesCounter.get();
        }
    }

    private class UploadReadHandler implements ReadHandler {

        private final int uploadNumber;
        private final NIOInputStream inputStream;

        private final FileOutputStream fileOutputStream;
        
        private final byte[] buf;

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

        @Override
        public void onDataAvailable() throws Exception {
            writeAvail();
            
            inputStream.notifyAvailable(this);
        }

        @Override
        public void onAllDataRead() throws Exception {
            writeAvail();
            finish();
        }

        @Override
        public void onError(Throwable t) {
            LOGGER.log(Level.WARNING, "Upload #" + uploadNumber + ": failed", t);
            finish();
        }

        private void writeAvail() throws IOException {
            while (inputStream.isReady()) {
                final int readBytes = inputStream.read(buf);
                uploadedBytesCounter.addAndGet(readBytes);
                fileOutputStream.write(buf, 0, readBytes);
            }
        }

        private void finish() {
            try {
                fileOutputStream.close();
            } catch (IOException ignored) {
            }
        }
    }
}
