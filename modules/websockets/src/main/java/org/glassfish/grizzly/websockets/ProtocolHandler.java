/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.websockets;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.websockets.frametypes.BinaryFrameType;
import org.glassfish.grizzly.websockets.frametypes.TextFrameType;

public abstract class ProtocolHandler {
    protected Connection connection;
    private WebSocket webSocket;
    protected byte inFragmentedType;
    protected byte outFragmentedType;
    protected final boolean maskData;
    protected FilterChainContext ctx;
    protected boolean processingFragment;
    protected Charset utf8 = new StrictUtf8();
    protected CharsetDecoder currentDecoder = utf8.newDecoder();
    protected ByteBuffer remainder;
    protected WebSocketMappingData mappingData;
    
    public ProtocolHandler(boolean maskData) {
        this.maskData = maskData;
    }

    public HandShake handshake(FilterChainContext ctx,
            WebSocketApplication app,
            HttpContent request) {
        
        final HandShake handshake = createServerHandShake(request);
        app.handshake(handshake);
        
        final HttpResponsePacket response =
                ((HttpRequestPacket) request.getHttpHeader()).getResponse();
        
        handshake.respond(ctx, app, response);
        return handshake;
    }

    public final GrizzlyFuture<DataFrame> send(DataFrame frame) {
        return send(frame, null);
    }

    public GrizzlyFuture<DataFrame> send(DataFrame frame,
            CompletionHandler<DataFrame> completionHandler) {
        return write(frame, completionHandler);
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection handler) {
        this.connection = handler;
    }

    public FilterChainContext getFilterChainContext() {
        return ctx;
    }

    public void setFilterChainContext(FilterChainContext ctx) {
        this.ctx = ctx;
    }

    protected WebSocketMappingData getMappingData() {
        return mappingData;
    }

    protected void setMappingData(final WebSocketMappingData mappingData) {
        this.mappingData = mappingData;
    }

    public WebSocket getWebSocket() {
        return webSocket;
    }

    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    public boolean isMaskData() {
        return maskData;
    }

    public abstract byte[] frame(DataFrame frame);
/*
    public void readFrame() {
        while (connection.ready()) {
            try {
                unframe(buffer, parsingFrame).respond(getWebSocket());
            } catch (FramingException fe) {
                fe.printStackTrace();
                System.out.println("connection = " + connection);
                getWebSocket().close();
            }
        }
    }
*/

    public DataFrame toDataFrame(String data) {
        return toDataFrame(data, true);
    }
    
    public DataFrame toDataFrame(byte[] data) {
        return toDataFrame(data, true);
    }
    
    public DataFrame toDataFrame(String data, boolean last) {
        return new DataFrame(new TextFrameType(), data, last);
    }
    
    public DataFrame toDataFrame(byte[] data, boolean last) {
        return new DataFrame(new BinaryFrameType(), data, last);
    }

    public abstract HandShake createServerHandShake(HttpContent requestContent);

    public abstract HandShake createClientHandShake(URI uri);

    public GrizzlyFuture<DataFrame> send(byte[] data) {
        return send(toDataFrame(data));
    }

    public GrizzlyFuture<DataFrame> send(String data) {
        return send(toDataFrame(data));
    }

    public GrizzlyFuture<DataFrame> stream(boolean last, byte[] bytes, int off, int len) {
        return send(toDataFrame(bytes, last));
    }

    public GrizzlyFuture<DataFrame> stream(boolean last, String fragment) {
        return send(toDataFrame(fragment, last));
    }

    public GrizzlyFuture<DataFrame> close(int code, String reason) {
        final ClosingFrame closingFrame = new ClosingFrame(code, reason);
        return send(closingFrame,
                new EmptyCompletionHandler<DataFrame>() {

                    @Override
                    public void failed(final Throwable throwable) {
                        webSocket.onClose(closingFrame);
                    }

                    @Override
                    public void completed(DataFrame result) {
                        if (!maskData) {
                            webSocket.onClose(closingFrame);
                        }
                    }
                });
    }

    @SuppressWarnings({"unchecked"})
    private GrizzlyFuture<DataFrame> write(final DataFrame frame,
            final CompletionHandler<DataFrame> completionHandler) {
        
        final Connection localConnection = connection;
        if (localConnection == null) {
            throw new IllegalStateException("Connection is null");
        }
        
        final FutureImpl<DataFrame> localFuture = SafeFutureImpl.<DataFrame>create();

        localConnection.write(frame, new EmptyCompletionHandler() {
            @Override
            public void completed(final Object result) {
                if (completionHandler != null) {
                    completionHandler.completed(frame);
                }

                localFuture.result(frame);
            }

            @Override
            public void failed(final Throwable throwable) {
                if (completionHandler != null) {
                    completionHandler.failed(throwable);
                }

                localFuture.failure(throwable);
            }
        });

        return localFuture;
    }

    public DataFrame unframe(Buffer buffer) {
        return parse(buffer);
    }

    public abstract DataFrame parse(Buffer buffer);

    /**
     * Convert a byte[] to a long.  Used for rebuilding payload length.
     */
    public long decodeLength(byte[] bytes) {
        return Utils.toLong(bytes, 0, bytes.length);
    }

    /**
     * Converts the length given to the appropriate framing data: <ol> <li>0-125 one element that is the payload length.
     * <li>up to 0xFFFF, 3 element array starting with 126 with the following 2 bytes interpreted as a 16 bit unsigned
     * integer showing the payload length. <li>else 9 element array starting with 127 with the following 8 bytes
     * interpreted as a 64-bit unsigned integer (the high bit must be 0) showing the payload length. </ol>
     *
     * @param length the payload size
     *
     * @return the array
     */
    public byte[] encodeLength(final long length) {
        byte[] lengthBytes;
        if (length <= 125) {
            lengthBytes = new byte[1];
            lengthBytes[0] = (byte) length;
        } else {
            byte[] b = Utils.toArray(length);
            if (length <= 0xFFFF) {
                lengthBytes = new byte[3];
                lengthBytes[0] = 126;
                System.arraycopy(b, 6, lengthBytes, 1, 2);
            } else {
                lengthBytes = new byte[9];
                lengthBytes[0] = 127;
                System.arraycopy(b, 0, lengthBytes, 1, 8);
            }
        }
        return lengthBytes;
    }

    protected void validate(final byte fragmentType, byte opcode) {
        if (fragmentType != 0 && opcode != fragmentType && !isControlFrame(opcode)) {
            throw new WebSocketException("Attempting to send a message while sending fragments of another");
        }
    }

    protected abstract boolean isControlFrame(byte opcode);

    protected byte checkForLastFrame(DataFrame frame, byte opcode) {
        byte local = opcode;
        if (!frame.isLast()) {
            validate(outFragmentedType, local);
            if (outFragmentedType != 0) {
                local = 0x00;
            } else {
                outFragmentedType = local;
                local &= 0x7F;
            }
        } else if (outFragmentedType != 0) {
            local = (byte) 0x80;
            outFragmentedType = 0;
        } else {
            local |= 0x80;
        }
        return local;
    }

    public void doClose() {
        final Connection localConnection = connection;
        if (localConnection == null) {
            throw new IllegalStateException("Connection is null");
        }

        localConnection.closeSilently();
    }

    protected void utf8Decode(boolean finalFragment, byte[] data, DataFrame dataFrame) {
            final ByteBuffer b = getByteBuffer(data);
            int n = (int) (b.remaining() * currentDecoder.averageCharsPerByte());
            CharBuffer cb = CharBuffer.allocate(n);
            for (; ; ) {
                CoderResult result = currentDecoder.decode(b, cb, finalFragment);
                if (result.isUnderflow()) {
                    if (finalFragment) {
                        currentDecoder.flush(cb);
                        if (b.hasRemaining()) {
                            throw new IllegalStateException("Final UTF-8 fragment received, but not all bytes consumed by decode process");
                        }
                        currentDecoder.reset();
                    } else {
                        if (b.hasRemaining()) {
                            remainder = b;
                        }
                    }
                    cb.flip();
                    String res = cb.toString();
                    dataFrame.setPayload(res);
                    dataFrame.setPayload(Utf8Utils.encode(new StrictUtf8(), res));
                    break;
                }
                if (result.isOverflow()) {
                    CharBuffer tmp = CharBuffer.allocate(2 * n + 1);
                    cb.flip();
                    tmp.put(cb);
                    cb = tmp;
                    continue;
                }
                if (result.isError() || result.isMalformed()) {
                    throw new Utf8DecodingError("Illegal UTF-8 Sequence");
                }
            }
        }

        protected ByteBuffer getByteBuffer(final byte[] data) {
            if (remainder == null) {
                return ByteBuffer.wrap(data);
            } else {
                final int rem = remainder.remaining();
                final byte[] orig = remainder.array();
                byte[] b = new byte[rem + data.length];
                System.arraycopy(orig, orig.length - rem, b, 0, rem);
                System.arraycopy(data, 0, b, rem, data.length);
                remainder = null;
                return ByteBuffer.wrap(b);
            }
        }
}
