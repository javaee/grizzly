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

package org.glassfish.grizzly.http.multipart;

import java.io.IOException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.io.NIOInputStream;
import org.glassfish.grizzly.http.util.Ascii;
import org.glassfish.grizzly.http.util.Constants;
import org.glassfish.grizzly.http.util.Header;

/**
 * {@link ReadHandler}, which implements the miltipart message parsing logic
 * and delegates control to a {@link MultipartEntryHandler}, when {@link MultipartEntry}
 * data becomes available.
 * 
 * @since 2.0.1
 * 
 * @author Alexey Stashok
 */
public class MultipartReadHandler implements ReadHandler {

    private enum State {
        PREAMBLE, PARSE_MULTIPART_ENTRY_HEADERS, START_BODY, BODY, RESET
    }

    private final Request request;
    private final CompletionHandler<Request> requestCompletionHandler;
    
    private final MultipartEntry multipartMixedEntry;
    private final CompletionHandler<MultipartEntry> multipartMixedCompletionHandler;

    private final NIOInputStream parentInputStream;
    
    private final MultipartEntryHandler multipartHandler;
    private final MultipartContext multipartContext;
    private final String boundary;

    private final Line line = new Line();

    private final MultipartEntry multipartEntry;
    
    private State state = State.PREAMBLE;

    private boolean isFinished;

    private boolean isMultipartMixed;
    
    public MultipartReadHandler(final Request request,
            final MultipartEntryHandler multipartHandler,
            final CompletionHandler<Request> completionHandler,
            final MultipartContext multipartContext) {
        this.request = request;
        this.multipartHandler = multipartHandler;
        this.requestCompletionHandler = completionHandler;
        this.multipartContext = multipartContext;
        this.boundary = multipartContext.getBoundary();
        this.parentInputStream = request.getNIOInputStream();

        multipartMixedCompletionHandler = null;
        multipartMixedEntry = null;
        
        multipartEntry = new MultipartEntry(multipartContext);
    }

    public MultipartReadHandler(final MultipartEntry parentMultipartEntry,
            final MultipartEntryHandler multipartHandler,
            final CompletionHandler<MultipartEntry> completionHandler,
            final MultipartContext multipartContext) {
        this.multipartMixedEntry = parentMultipartEntry;
        this.multipartHandler = multipartHandler;
        this.multipartMixedCompletionHandler = completionHandler;
        this.multipartContext = multipartContext;
        this.boundary = multipartContext.getBoundary();

        this.parentInputStream = parentMultipartEntry.getNIOInputStream();

        request = null;
        requestCompletionHandler = null;
        isMultipartMixed = true;

        multipartEntry = new MultipartEntry(multipartContext);
    }

    @Override
    public void onDataAvailable() throws Exception {
        if (!process()) {
            final int totalBytesAvailable =  multipartEntry.getReservedBytes() +
                    multipartEntry.availableBytes() + line.len;

            parentInputStream.notifyAvailable(this, totalBytesAvailable + 1);
        } else {
            checkComplete();
        }
    }

    @Override
    public void onAllDataRead() throws Exception {
        process();
        checkComplete();
    }

    private void checkComplete() {
        if (isFinished) {
            if (isMultipartMixed) {
                checkMultipartMixedComplete(multipartMixedCompletionHandler);
            } else {
                checkRequestComplete(requestCompletionHandler);
            }
        }
    }

    private void checkMultipartMixedComplete(
            final CompletionHandler<MultipartEntry> multipartMixedCompletionHandler) {
        
        if (multipartMixedCompletionHandler != null) {
            multipartMixedCompletionHandler.completed(multipartMixedEntry);
        }
    }

    private void checkRequestComplete(
            final CompletionHandler<Request> requestCompletionHandler) {

        if (requestCompletionHandler != null) {
            requestCompletionHandler.completed(request);
        }
    }

    @Override
    public void onError(Throwable t) {
        final CompletionHandler<Request> localCompletionHandler = requestCompletionHandler;
        if (localCompletionHandler != null) {
            localCompletionHandler.failed(t);
        }
//        System.out.println("MultipartReadHandler ERROR");
//        t.printStackTrace(System.out);
    }

    private boolean process() throws Exception {
        do {
            switch(state) {
                case PREAMBLE:
                {
                    if (!skipPreamble()) {
                        return false;
                    }

                    if (isFinished) {
                        return true;
                    }
                }

                case PARSE_MULTIPART_ENTRY_HEADERS:
                {
                    if (!parseHeaders()) {
                        return false;
                    }
                    
                    finishHeadersParsing();
                }

                case START_BODY:
                {
                    state = State.BODY;
//                    feedMultipartEntry();
                    multipartHandler.handle(multipartEntry);

//                    if (!multipartEntry.isFinished()) {
//                        return false;
//                    }
//
//                    state = State.RESET;
//                    break;
                }

                case BODY:
                {
                    feedMultipartEntry();
                    if (!multipartEntry.isFinished()) {
                        return false;
                    }

                    state = State.RESET;
                    break;
                }

                case RESET:
                {
                    multipartEntry.reset();

                    if (isFinished) {
                        return true;
                    }
                    
                    state = State.PARSE_MULTIPART_ENTRY_HEADERS;
                }
            }
        } while (true);
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    private void feedMultipartEntry() throws Exception {
//        int available = 0;
        boolean isComplete;
        
        do {
            line.offset = multipartEntry.availableBytes() + multipartEntry.getReservedBytes();

            readLine();

            isComplete = line.isComplete;
//            System.out.println("Line=" + line.toString() + " " + isComplete);
            
            if (isComplete) {
                if (line.isBoundary()) {
                    isFinished = line.isFinalBoundary;
                    
                    multipartEntry.onFinished();

                    try {
                        // Skip the boundary + all the leftovers from the prev.
                        // multipart entry
                        parentInputStream.skip(multipartEntry.availableBytes()
                                + multipartEntry.getReservedBytes() + line.len);
                    } catch (IOException ignored) {
                        // should never happen
                    }

//                    line.skip();
                    line.reset();
                    return;
                } else {
                    final int lineTerminatorLength = line.getLineTerminatorLength();

                    multipartEntry.addAvailableBytes(line.len +
                            multipartEntry.getReservedBytes() - lineTerminatorLength);

                    multipartEntry.setReservedBytes(lineTerminatorLength);
//                    available += line.len;
                    line.reset();
                }
            } else {
                // if line is incomplete - we always make available line.len - 1
                // bytes (cause the last byte can be CR).
                // Also we have to make sure the incomplete line is not a boundary
                if (line.len > 1 && !line.couldBeBoundary()) {
                    multipartEntry.addAvailableBytes((line.len - 1) +
                            multipartEntry.getReservedBytes());

                    line.len = 1;
                    multipartEntry.setReservedBytes(0);
                }
            }
        } while (isComplete);

        multipartEntry.onDataReceived();
    }

    private boolean skipPreamble() {
        do {
            readLine();
            if (!line.isComplete) {
                break;
            }

            final boolean isSectionBoundary = line.isBoundary();
            isFinished = line.isFinalBoundary;

            line.skip();
            
            line.reset();
            
            if (isSectionBoundary) {
                state = State.PARSE_MULTIPART_ENTRY_HEADERS;
                return true;
            }
        } while(true);

        return false;
    }

    private boolean parseHeaders() {
        do {
            readLine();

            if (!line.isComplete) {
                return false;
            }

            if (!line.hasContent()) {
                // end of the headers
                line.skip();
                line.reset();
                return true;
            }

            setHeader();
            line.skip();
            line.reset();
            
        } while (true);
    }

    private void finishHeadersParsing() {
        state = State.START_BODY;

        if (isMultipartMixed) {
            multipartEntry.initialize(multipartMixedEntry.getNIOInputStream());
        } else {
            multipartEntry.initialize(request.getNIOInputStream());
        }
        
        final String contentType = multipartEntry.getHeader(Header.ContentType);
        if (contentType != null) {
            multipartEntry.setContentType(contentType);
        }

        final String contentDisposition = multipartEntry.getHeader(Header.ContentDisposition);
        if (contentDisposition != null) {
            multipartEntry.setContentDisposition(
                    new ContentDisposition(contentDisposition));
        }
    }

    private void setHeader() {
        final Buffer buffer = parentInputStream.getBuffer();
        final int position = buffer.position();
        final int contentLength = line.len - line.getLineTerminatorLength();

        final int colonIdx = findEndOfHeaderName(buffer, position,
                position + contentLength);

        final String name;
        final String value;
        
        if (colonIdx == -1) {
            name = trim(buffer, position,
                    position + contentLength);
            value = null;
        } else {
            name = trim(buffer, position, colonIdx);
            value = trim(buffer, colonIdx + 1,
                    position + contentLength);
        }

        if (name == null) {
            return;
        }
        
        multipartEntry.setHeader(name, value);
    }

    void readLine() {
        final Buffer buffer = parentInputStream.getBuffer();

        final int position = buffer.position() + line.offset;
//        final int limit = buffer.limit();
        final int limit = buffer.position() + parentInputStream.readyData();
        int offset = position + line.len;

        while(offset < limit) {
            final byte b = buffer.get(offset++);

            if (b == Constants.LF) {
                line.isCrLf = position <= offset - 2 &&
                        buffer.get(offset - 2) == Constants.CR;
                line.isComplete = true;
                break;
            }
        }

        line.len = offset - position;
    }

    private int findEndOfHeaderName(final Buffer buffer, int position,
            final int limit) {
        while (position < limit) {
            final byte b = buffer.get(position);
            if (b == ':') {
                return position;
            }

            // lowercase the header name
            buffer.put(position, (byte) Ascii.toLower(b));
            position++;
        }

        return -1;
    }

    private String trim(final Buffer buffer, int position, int limit) {
        while(position < limit) {
            // skip whitespaces left
            if (buffer.get(position) > 32) {
                break;
            }
            
            position++;
        }

        while (position < limit) {
            // skip whitespaces right
            if (buffer.get(limit - 1) > 32) {
                break;
            }

            limit--;
        }

        if (position == limit) {
            return null;
        }

        return buffer.toStringContent(null, position, limit);
    }

    private class Line {
        boolean isCrLf;
        
        boolean isComplete;
        int len;
        int offset;

        // Offset, which remembers where the last couldBeBoundary check was finished
        int couldBeBoundaryOffset;

        boolean isBoundary;
        boolean isFinalBoundary;

        public void reset() {
            isCrLf = false;
            isComplete = false;
            len = 0;
            offset = 0;
            couldBeBoundaryOffset = 0;
            isBoundary = false;
            isFinalBoundary = false;
        }

        public boolean hasContent() {
            return (isCrLf && len > 2) || (!isCrLf && len > 1);
        }

        private boolean isBoundary() {
            return isBoundary || parseBoundary();
        }

        private boolean parseBoundary() {
            final int lineTerminatorLength = getLineTerminatorLength();
            final int boundaryLength = boundary.length();
            // '+ 2' for additional '--' prefix
            final boolean isLookingSectionBoundary = (len == boundaryLength + 2 + lineTerminatorLength);
            final boolean isLookingFinalBoundary =  (len == boundaryLength + 2 + lineTerminatorLength + 2);
            if (!isLookingSectionBoundary && !isLookingFinalBoundary) {
                return false;
            }

            final Buffer buffer = parentInputStream.getBuffer();
            final int position = buffer.position() + offset;

            // if we called couldBeBoundary() for the incomplete boundary line - let's reuse its findings
            int checkIdx = couldBeBoundaryOffset;

            if (checkIdx < 2) {
                if (buffer.get(position) != '-' || buffer.get(position + 1) != '-') {
                    return false;
                }

                checkIdx = 2;
            }

            // if we called couldBeBoundary() for the incomplete boundary line - let's reuse its findings
            for (int i = checkIdx; i < boundaryLength + 2; i++) {
                // '+ 2' because of '--' prefix
                if (buffer.get(position + i) != boundary.charAt(i - 2)) {
                    return false;
                }
            }

            isBoundary = true;
            
            if (isLookingFinalBoundary) {
                if (buffer.get(position + 2 + boundaryLength) == '-' &&
                        buffer.get(position + 2 + boundaryLength + 1) == '-') {
                    isFinalBoundary = true;
                }
            }
            
            return true;
        }

        private boolean couldBeBoundary() {
            // 2 + 4 means 2 bytes for line terminator, 4 - for prefix and postfix '--'.
            if (len > boundary.length() + 2 + 4) {
                return false;
            }
            
            final Buffer buffer = parentInputStream.getBuffer();
            final int position = buffer.position();

            for (; couldBeBoundaryOffset < 2 && couldBeBoundaryOffset < len; couldBeBoundaryOffset++) {
                if (buffer.get(offset + position + couldBeBoundaryOffset) != '-') {
                    return false;
                }
            }

            final int boundaryLength = boundary.length();
            for (; couldBeBoundaryOffset < line.len && couldBeBoundaryOffset < boundaryLength; couldBeBoundaryOffset++) {
                if (buffer.get(offset + position + couldBeBoundaryOffset) != boundary.charAt(couldBeBoundaryOffset - 2)) {
                    return false;
                }
            }

            return true;
        }
        
        private int getLineTerminatorLength() {
            return 1 + (isCrLf ? 1 : 0);
        }

        @SuppressWarnings({"ResultOfMethodCallIgnored"})
        private void skip() {
            try {
                parentInputStream.skip(line.len);
            } catch (IOException ignored) {
                // shouldn't get here
            }
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            if (len > 0) {
                final Buffer buffer = parentInputStream.getBuffer();
                final int start = buffer.position() + offset;
                
                sb.append(buffer.toStringContent(null, start, start + len));
            }

            return sb.toString();
        }
    }
}
