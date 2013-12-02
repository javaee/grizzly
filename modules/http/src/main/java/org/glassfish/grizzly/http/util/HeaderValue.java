/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import static org.glassfish.grizzly.http.util.HttpCodecUtils.*;

/**
 * This class serves as an HTTP header value holder, plus it implements useful
 * utility methods to optimize headers serialization.
 * 
 * @author Alexey Stashok
 */
public final class HeaderValue {
    public static final HeaderValue IDENTITY = newHeaderValue("identity").prepare();
    
    private final String value;
    private byte[] preparedByteArray;
    
    
    /**
     * Creates a {@link HeaderValue} wrapper over a {@link String}
     * header value representation.
     * 
     * @param value {@link String} header value representation
     * @return a {@link HeaderValue} wrapper over a {@link String}
     *         heade value representation
     */
    public static HeaderValue newHeaderValue(final String value) {
        return new HeaderValue(value);
    }

    private HeaderValue(final String value) {
        this.value = value;
    }
    
    /**
     * Prepare the <tt>HeaderValue</tt> for the serialization.
     * 
     * This method might be particularly useful if we use the same <tt>HeaderValue</tt>
     * over and over for different responses, so that the <tt>HeaderValue</tt>
     * will not have to be parsed and prepared for each response separately.
     * 
     * @return this <tt>HeaderValue</tt>
     */
    public HeaderValue prepare() {
        if (preparedByteArray == null) {
            getByteArray();
        }
        
        return this;
    }

    /**
     * @return <tt>true</tt> if header value is not null, or
     *         <tt>false</tt> otherwise
     */
    public boolean isSet() {
        return value != null;
    }
    
    /**
     * @return the header value string
     */
    public String get() {
        return value;
    }
    
    /**
     * @return the byte array representation of the header value
     */
    public byte[] getByteArray() {
        // if there's prepared byte array - return it
        if (preparedByteArray != null) {
            return preparedByteArray;
        }
        
        if (value == null) {
            return EMPTY_ARRAY;
        }
        
        preparedByteArray = toCheckedByteArray(value);
        return preparedByteArray;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Serializes this <tt>HeaderValue</tt> value into a passed {@link DataChunk}.
     * 
     * @param dc {@link DataChunk}
     */
    public void serializeToDataChunk(final DataChunk dc) {
        if (preparedByteArray != null) {
            dc.setBytes(preparedByteArray);
        } else {
            dc.setString(value);
        }
    }
}
