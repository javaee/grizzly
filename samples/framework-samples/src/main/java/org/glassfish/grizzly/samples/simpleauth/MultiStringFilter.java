/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.samples.simpleauth;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.BufferOutputStream;
import org.glassfish.grizzly.utils.StringFilter;
 
/**
 * MultiString filter, the codec, that converts Buffer <-> List&lt;String&gt;
 * 
 * @author Grizzly team
 */
public class MultiStringFilter extends BaseFilter {
 
    private static final Logger LOGGER = Grizzly.logger(StringFilter.class);
    
    protected final Charset charset;
    
    protected final Attribute<DecodeResult> decodeStateAttr;
 
    protected final byte[] stringTerminateBytes;
 
    public MultiStringFilter() {
        this(null, null);
    }
    
    public MultiStringFilter(Charset charset) {
        this(charset, null);
    }
 
    public MultiStringFilter(Charset charset, String stringTerminate) {
        if (charset != null) {
            this.charset = charset;
        } else {
            this.charset = Charset.defaultCharset();
        }
        
        if (stringTerminate != null) {
            stringTerminateBytes = stringTerminate.getBytes(charset);
        } else {
            stringTerminateBytes = null;
        }
 
        decodeStateAttr = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                MultiStringFilter.class.getName() + ".string-length");
    }
 
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        
        final DecodeResult prevState = decodeStateAttr.get(connection);
        final Buffer input = ctx.getMessage();
 
        if (!input.hasRemaining()) {
            return ctx.getStopAction();
        }
        
        List<String> stringList = null;
        DecodeResult newState;
        do {
            newState = decode(input, prevState);
            if (newState.isDone()) {
                if (stringList == null) {
                    stringList = createInList();
                }
                
                stringList.add(newState.getResult());
                newState.reset();
            } else {
                break;
            }
        } while (input.hasRemaining());
        
        if (newState.isClear() && prevState != null) {
            decodeStateAttr.remove(connection);
        } else if (!newState.isClear() && prevState == null) {
            decodeStateAttr.set(connection, newState);
        }
        
        if (stringList != null) { // if != null - it means there's at least 1 element
            final Buffer remainder = input.split(input.position());
            input.tryDispose();
 
            ctx.setMessage(stringList);
            
            return ctx.getInvokeAction(remainder, Buffers.getBufferAppender(true));
        }
        
        return ctx.getStopAction(input);
    }
 
    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        
        final Object input = ctx.getMessage();
        if (!(input instanceof List)) {
            return ctx.getInvokeAction();
        }
        
        final List<String> inputStringList = ctx.getMessage();
        final Buffer output = encode(ctx.getMemoryManager(), inputStringList);
 
        ctx.setMessage(output);
        
        return ctx.getInvokeAction();
    }
 
    public DecodeResult decode(final Buffer inputBuffer,
            final DecodeResult decodeResult) {
        return stringTerminateBytes == null ?
                parseWithLengthPrefix(inputBuffer, decodeResult) :
                parseWithTerminatingSeq(inputBuffer, decodeResult);
    }
    
    public Buffer encode(final MemoryManager mm, final List<String> inputStringList) throws IOException {
        final String charsetName = charset.name();
        final BufferOutputStream baos = new BufferOutputStream(mm,
                mm.allocate(1024));
        final DataOutputStream dos = new DataOutputStream(baos);
 
        try {
            for (String inputString : inputStringList) {
                byte[] byteRepresentation;
                try {
                    byteRepresentation = inputString.getBytes(charsetName);
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalStateException("Charset "
                            + charset.name() + " is not supported", e);
                }
                if (stringTerminateBytes == null) {
                    dos.writeInt(byteRepresentation.length);
                }
 
                dos.write(byteRepresentation);
                if (stringTerminateBytes != null) {
                    dos.write(stringTerminateBytes);
                }
            }
        } finally {
            dos.close();
        }
 
        final Buffer output = baos.getBuffer();
        output.trim();
        output.allowBufferDispose(true);
        return output;
    }
    
    protected DecodeResult parseWithLengthPrefix(final Buffer input,
            DecodeResult decodeResult) {
        
        if (decodeResult == null) {
            decodeResult = new DecodeResult();
        } else if (decodeResult.isDone()) {
            return decodeResult;
        }
        
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "StringFilter decode stringSize={0} buffer={1} content={2}",
                    new Object[]{decodeResult.state, input, input.toStringContent()});
        }
 
        int stringSize = decodeResult.state;
        if (stringSize == -1) {
            if (input.remaining() < 4) {
                return decodeResult;
            }
 
            decodeResult.state = stringSize = input.getInt();
        }
        
        if (input.remaining() < stringSize) {
            return decodeResult;
        }
 
        String stringMessage = input.toStringContent(charset,
                input.position(), input.position() + stringSize);
 
        input.position(input.position() + stringSize);        
        decodeResult.done(stringMessage);
        
        return decodeResult;
    }
 
    protected DecodeResult parseWithTerminatingSeq(final Buffer input,
            DecodeResult decodeResult) {
        
        if (decodeResult == null) {
            decodeResult = new DecodeResult();
        } else if (decodeResult.isDone()) {
            return decodeResult;
        }
 
        final int terminationBytesLength = stringTerminateBytes.length;
        int checkIndex = 0;
        
        int termIndex = -1;
 
        int offset = 0;
        if (decodeResult.state != -1) {
            offset = decodeResult.state;
        }
 
        for(int i = input.position() + offset; i < input.limit(); i++) {
            if (input.get(i) == stringTerminateBytes[checkIndex]) {
                checkIndex++;
                if (checkIndex >= terminationBytesLength) {
                    termIndex = i - terminationBytesLength + 1;
                    break;
                }
            }
        }
 
        if (termIndex >= 0) {
            // Terminating sequence was found
            final String stringMessage = input.toStringContent(
                    charset, input.position(), termIndex);
            
            decodeResult.done(stringMessage);
            
            input.position(termIndex + terminationBytesLength);
        } else {
            offset = input.remaining() - terminationBytesLength;
            if (offset < 0) {
                offset = 0;
            }
 
            decodeResult.state = offset;
        }
        
        return decodeResult;
    }
 
    protected List<String> createInList() {
        return new ArrayList<String>(4);
    }
    
    public static class DecodeResult {
        private String result;
        
        private int state = -1;
        
 
        public boolean isDone() {
            return result != null;
        }
 
        public String getResult() {
            return result;
        }
 
        private void done(String result) {
            this.result = result;
        }
 
        void reset() {
            state = -1;
            result = null;
        }
 
        private boolean isClear() {
            return state == -1;
        }
    }
}

