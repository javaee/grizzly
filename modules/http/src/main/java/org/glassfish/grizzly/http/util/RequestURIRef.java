/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.util;

import org.glassfish.grizzly.Buffer;
import java.io.CharConversionException;
import java.nio.charset.Charset;
import org.glassfish.grizzly.memory.Buffers;
import static org.glassfish.grizzly.utils.Charsets.*;

/**
 * Request URI holder.
 * Contains 3 types of URI:
 *      1) Original, which represents the URI's original state at time it was set.
 *      2) Decoded, which has represents URI after being URI and String decoded (internally used by Mapper).
 *      3) Actual, which is supposed as URI value returned to user.
 * 
 * @author Alexey Stashok
 */
public class RequestURIRef {
    private boolean isDecoded;
    private Charset decodedURIEncoding;
    private boolean wasSlashAllowed = true;

    private Charset defaultURIEncoding = UTF8_CHARSET;

    // Original Request URI
    private final DataChunk originalRequestURIDC = DataChunk.newInstance();
    // Actual Request URI
    private final DataChunk requestURIDC = new DataChunk() {
        @Override
        public void notifyDirectUpdate() {
            if (type == Type.Buffer) {
                final int start = getStart();
                final int end = getEnd();

                final byte[] bytes = new byte[end - start];
                
                final Buffer currentBuffer = getBufferChunk().getBuffer();
                final int pos = currentBuffer.position();
                final int lim = currentBuffer.limit();

                Buffers.setPositionLimit(currentBuffer, start, end);
                currentBuffer.get(bytes);
                Buffers.setPositionLimit(currentBuffer, pos, lim);

                setBytes(bytes);
            }
        }
    };
    
    // Decoded Request URI
    private final DataChunk decodedRequestURIDC = DataChunk.newInstance();

    private byte[] preallocatedDecodedURIBuffer;

    public void init(final Buffer input, final int start, final int end) {
        originalRequestURIDC.setBuffer(input, start, end);
        requestURIDC.setBuffer(input, start, end);
    }

    public void init(final byte[] input, final int start, final int end) {
        originalRequestURIDC.setBytes(input, start, end);
        requestURIDC.setBytes(input, start, end);
    }
    
    public void init(final String requestUri) {
        originalRequestURIDC.setString(requestUri);
        requestURIDC.setString(requestUri);
    }

    public final DataChunk getOriginalRequestURIBC() {
        return originalRequestURIDC;
    }

    public final DataChunk getRequestURIBC() {
        return requestURIDC;
    }

    public final DataChunk getDecodedRequestURIBC() throws CharConversionException {
        return getDecodedRequestURIBC(wasSlashAllowed, defaultURIEncoding);
    }

    public DataChunk getDecodedRequestURIBC(boolean isSlashAllowed)
            throws CharConversionException {
        return getDecodedRequestURIBC(isSlashAllowed, defaultURIEncoding);
    }

    public DataChunk getDecodedRequestURIBC(final boolean isSlashAllowed,
            final Charset charset) throws CharConversionException {

        if (isDecoded && isSlashAllowed == wasSlashAllowed
                && charset == decodedURIEncoding) {
            return decodedRequestURIDC;
        }

        checkDecodedURICapacity(requestURIDC.getLength());
        decodedRequestURIDC.setBytes(preallocatedDecodedURIBuffer);

        HttpRequestURIDecoder.decode(requestURIDC, decodedRequestURIDC,
                isSlashAllowed, charset);
        
        isDecoded = true;
        wasSlashAllowed = isSlashAllowed;

        decodedURIEncoding = charset;
        return decodedRequestURIDC;
    }

    public String getURI() {
        return getURI(null);
    }

    public String getURI(final Charset encoding) {
        return getRequestURIBC().toString(encoding);
    }

    public void setURI(final String uri) {
        getRequestURIBC().setString(uri);
    }

    public final String getDecodedURI() throws CharConversionException {
        return getDecodedURI(wasSlashAllowed);
    }

    public final String getDecodedURI(final boolean isSlashAllowed)
            throws CharConversionException {
        return getDecodedURI(isSlashAllowed, defaultURIEncoding);
    }

    public String getDecodedURI(final boolean isSlashAllowed, Charset encoding)
            throws CharConversionException {
        
        getDecodedRequestURIBC(isSlashAllowed, encoding);

        return decodedRequestURIDC.toString();
    }

    public void setDecodedURI(String uri) {
        decodedRequestURIDC.setString(uri);
        isDecoded = true;
    }

    public boolean isDecoded() {
        return isDecoded;
    }

    public Charset getDefaultURIEncoding() {
        return defaultURIEncoding;
    }

    public void setDefaultURIEncoding(Charset defaultURIEncoding) {
        this.defaultURIEncoding = defaultURIEncoding;
    }

    public void recycle() {
        originalRequestURIDC.recycle();
        decodedRequestURIDC.recycle();
        requestURIDC.recycle();

        isDecoded = false;
        wasSlashAllowed = true;
        decodedURIEncoding = null;
        defaultURIEncoding = UTF8_CHARSET;
    }

    private void checkDecodedURICapacity(final int size) {
        if (preallocatedDecodedURIBuffer == null ||
                preallocatedDecodedURIBuffer.length < size) {
            // for static allocation it's better to wrap byte[]
            preallocatedDecodedURIBuffer = new byte[size];
        }
    }
}
