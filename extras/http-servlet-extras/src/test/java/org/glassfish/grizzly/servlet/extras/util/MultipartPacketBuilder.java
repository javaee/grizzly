/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.servlet.extras.util;

import java.nio.charset.Charset;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.server.Constants;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

import java.util.ArrayList;
import java.util.List;

import static org.glassfish.grizzly.http.util.HttpCodecUtils.put;
/**
 * HTTP Multi-part packet builder
 * 
 * @author Alexey Stashok
 */
public class MultipartPacketBuilder {
    private static final Charset DEFAULT_HTTP_CHARSET = org.glassfish.grizzly.http.util.Constants.DEFAULT_HTTP_CHARSET;
    private static final byte[] DOUBLE_DASH = "--".getBytes(DEFAULT_HTTP_CHARSET);
    private static final byte[] CONTENT_DISPOSITION_BYTES = "Content-Disposition".getBytes(DEFAULT_HTTP_CHARSET);
    private static final byte[] CONTENT_TYPE_BYTES = "Content-Type".getBytes(DEFAULT_HTTP_CHARSET);
    private final byte[] tempEncodingBuffer = new byte[512];
    
    private final String boundary;

    private final List<MultipartEntryPacket> multipartEntries =
            new ArrayList<MultipartEntryPacket>();

    private String epilogue;
    private String preamble;

    private MultipartPacketBuilder(final String boundary) {
        this.boundary = boundary;
    }

    public static MultipartPacketBuilder builder(final String boundary) {
        return new MultipartPacketBuilder(boundary);
    }

    public MultipartPacketBuilder addMultipartEntry(final MultipartEntryPacket multipartEntry) {
        multipartEntries.add(multipartEntry);
        return this;
    }

    public MultipartPacketBuilder removeMultipartEntry(final MultipartEntryPacket multipartEntry) {
        multipartEntries.remove(multipartEntry);
        return this;
    }

    public MultipartPacketBuilder preamble(String preamble) {
        this.preamble = preamble;
        return this;
    }

    public MultipartPacketBuilder epilogue(String epilogue) {
        this.epilogue = epilogue;
        return this;
    }

    public Buffer build() {
        final MemoryManager memoryManager = MemoryManager.DEFAULT_MEMORY_MANAGER;
        Buffer resultBuffer = null;

        boolean isFirst = true;
        for (MultipartEntryPacket entry : multipartEntries) {
            
            Buffer headerBuffer = memoryManager.allocate(2048);

            if (!isFirst) {
                headerBuffer = put(memoryManager, headerBuffer, Constants.CRLF_BYTES);
            } else {
                if (preamble != null) {
                    headerBuffer = put(memoryManager, headerBuffer, tempEncodingBuffer, preamble);
                    headerBuffer = put(memoryManager, headerBuffer, Constants.CRLF_BYTES);
                }
                
                isFirst = false;
            }

            headerBuffer = put(memoryManager, headerBuffer, DOUBLE_DASH);
            headerBuffer = put(memoryManager, headerBuffer, tempEncodingBuffer, boundary);
            headerBuffer = put(memoryManager, headerBuffer, Constants.CRLF_BYTES);
            
            for (String headerName : entry.getHeaderNames()) {
                String headerValue = entry.getHeader(headerName);
                setHeader(memoryManager, headerBuffer, tempEncodingBuffer, headerName, headerValue);
            }

            if (entry.getContentDisposition() != null) {
                setHeader(memoryManager, headerBuffer, tempEncodingBuffer,
                        CONTENT_DISPOSITION_BYTES, entry.getContentDisposition());
            }

            if (entry.getContentType() != null) {
                setHeader(memoryManager, headerBuffer, tempEncodingBuffer,
                        CONTENT_TYPE_BYTES, entry.getContentType());
            }

            headerBuffer = put(memoryManager, headerBuffer, Constants.CRLF_BYTES);
            headerBuffer.trim();
            resultBuffer = Buffers.appendBuffers(memoryManager, resultBuffer,
                    headerBuffer);
            resultBuffer = Buffers.appendBuffers(memoryManager, resultBuffer,
                    entry.getContent());

            isFirst = false;
        }
        
        Buffer trailerBuffer = memoryManager.allocate(boundary.length() + 8);
        trailerBuffer = put(memoryManager, trailerBuffer, Constants.CRLF_BYTES);
        trailerBuffer = put(memoryManager, trailerBuffer, DOUBLE_DASH);
        trailerBuffer = put(memoryManager, trailerBuffer, tempEncodingBuffer, boundary);
        trailerBuffer = put(memoryManager, trailerBuffer, DOUBLE_DASH);
        trailerBuffer = put(memoryManager, trailerBuffer, Constants.CRLF_BYTES);

        if (epilogue != null) {
            trailerBuffer = put(memoryManager, trailerBuffer, tempEncodingBuffer, epilogue);
        }

        trailerBuffer.flip();

        resultBuffer = Buffers.appendBuffers(memoryManager, resultBuffer, trailerBuffer);
        
        return resultBuffer;
    }

    private static void setHeader(final MemoryManager memoryManager,
                                  Buffer headerBuffer,
                                  byte[] tempEncodingBuffer,
                                  String headerName,
                                  String headerValue) {
        
        headerBuffer = put(memoryManager, headerBuffer, tempEncodingBuffer, headerName);
        headerBuffer = put(memoryManager, headerBuffer, Constants.COLON_BYTES);
        headerBuffer = put(memoryManager, headerBuffer, null, headerValue);
        put(memoryManager, headerBuffer, Constants.CRLF_BYTES);
    }

    private static void setHeader(final MemoryManager memoryManager,
                                  Buffer headerBuffer,
                                  byte[] tempEncodingBuffer,
                                  byte[] headerName,
                                  String headerValue) {

        headerBuffer = put(memoryManager, headerBuffer, headerName);
        headerBuffer = put(memoryManager, headerBuffer, Constants.COLON_BYTES);
        headerBuffer = put(memoryManager, headerBuffer, tempEncodingBuffer, headerValue);
        put(memoryManager, headerBuffer, Constants.CRLF_BYTES);
    }

}
