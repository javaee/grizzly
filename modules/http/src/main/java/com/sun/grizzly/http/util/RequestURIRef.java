/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.util;

import java.io.CharConversionException;
import java.nio.charset.Charset;

/**
 *
 * @author Alexey Stashok
 */
public class RequestURIRef {
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    
    private final BufferChunk requestURIBC = BufferChunk.newInstance();
    private boolean isDecoded;
    private boolean wasSlashAllowed = true;
    
    public final BufferChunk getRequestURIBC() {
        return requestURIBC;
    }

    public final BufferChunk getDecodedRequestURIBC() throws CharConversionException {
        return getDecodedRequestURIBC(wasSlashAllowed);
    }

    public BufferChunk getDecodedRequestURIBC(boolean isSlashAllowed)
            throws CharConversionException {
        
        if (isDecoded) {
            if (isSlashAllowed != wasSlashAllowed)
                throw new IllegalStateException(
                        "URI was already decoded with isSlashAllowed=" + wasSlashAllowed);
            
            return requestURIBC;
        }

        URLDecoder.decode(requestURIBC, isSlashAllowed);
        wasSlashAllowed = isSlashAllowed;
        isDecoded = true;
        return requestURIBC;
    }

    public String getURI() {
        return getURI(null);
    }

    public String getURI(Charset charset) {
        return requestURIBC.toString(charset);
    }

    public void setURI(String uri) {
        requestURIBC.setString(uri);
    }

    public final String getDecodedURI() throws CharConversionException {
        return getDecodedURI(wasSlashAllowed);
    }

    public final String getDecodedURI(final boolean isSlashAllowed) throws CharConversionException {
        return getDecodedURI(true, null);
    }

    public String getDecodedURI(final boolean isSlashAllowed, Charset charset)
            throws CharConversionException {
        
        if (isDecoded) {
            if (isSlashAllowed != wasSlashAllowed)
                throw new IllegalStateException(
                        "URI was already decoded with isSlashAllowed=" + wasSlashAllowed);

            return requestURIBC.toString(charset);
        }

        if (!requestURIBC.hasString()) {
            URLDecoder.decode(requestURIBC, isSlashAllowed);
        } else {
            if (charset == null) {
                charset = UTF8_CHARSET;
            }
            
            requestURIBC.setString(
                    URLDecoder.decode(requestURIBC.toString(charset), isSlashAllowed));
        }

        wasSlashAllowed = isSlashAllowed;
        isDecoded = true;
        return requestURIBC.toString(charset);
    }

    public void setDecodedURI(String uri) {
        requestURIBC.setString(uri);
        isDecoded = true;
    }

    public boolean isDecoded() {
        return isDecoded;
    }

    public void setDecoded(boolean isDecoded) {
        this.isDecoded = isDecoded;
    }

    public void recycle() {
        requestURIBC.recycle();
        isDecoded = false;
        wasSlashAllowed = true;
    }
}
