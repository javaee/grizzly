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

package org.glassfish.grizzly.http.util;

import java.io.CharConversionException;
import java.nio.charset.Charset;
import static org.glassfish.grizzly.http.util.Charsets.*;

/**
 *
 * @author Alexey Stashok
 */
public class RequestURIRef {
    private final DataChunk requestURIBC = DataChunk.newInstance();
    private boolean isDecoded;
    private Charset decodedURIEncoding;
    private boolean wasSlashAllowed = true;

    private Charset defaultURIEncoding = UTF8_CHARSET;
    
    public final DataChunk getRequestURIBC() {
        return requestURIBC;
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

        if (isDecoded) {
            if (isSlashAllowed != wasSlashAllowed)
                throw new IllegalStateException(
                        "URI was already decoded with isSlashAllowed=" + wasSlashAllowed);
            if (charset == decodedURIEncoding) {
                return requestURIBC;
            } else {
                HttpRequestURIDecoder.convertToChars(requestURIBC, charset);
            }
        } else {
            HttpRequestURIDecoder.decode(requestURIBC, isSlashAllowed, charset);
            isDecoded = true;
            wasSlashAllowed = isSlashAllowed;
        }

        decodedURIEncoding = charset;
        return requestURIBC;
    }

    public String getURI() {
        return getURI(null);
    }

    public String getURI(Charset encoding) {
        return requestURIBC.toString(encoding);
    }

    public void setURI(String uri) {
        requestURIBC.setString(uri);
    }

    public final String getDecodedURI() throws CharConversionException {
        return getDecodedURI(wasSlashAllowed);
    }

    public final String getDecodedURI(final boolean isSlashAllowed) throws CharConversionException {
        return getDecodedURI(isSlashAllowed, null);
    }

    public String getDecodedURI(final boolean isSlashAllowed, Charset encoding)
            throws CharConversionException {
        
        getDecodedRequestURIBC(isSlashAllowed, encoding);

        final String strValue = requestURIBC.toString();
        return strValue;
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

    public Charset getDefaultURIEncoding() {
        return defaultURIEncoding;
    }

    public void setDefaultURIEncoding(Charset defaultURIEncoding) {
        this.defaultURIEncoding = defaultURIEncoding;
    }

    public void recycle() {
        requestURIBC.recycle();
        isDecoded = false;
        wasSlashAllowed = true;
        decodedURIEncoding = null;
        defaultURIEncoding = UTF8_CHARSET;
    }
}
