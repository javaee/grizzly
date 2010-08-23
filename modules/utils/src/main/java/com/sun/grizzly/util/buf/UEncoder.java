/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.grizzly.util.buf;

import com.sun.grizzly.util.LoggerUtils;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.BitSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Efficient implementation for encoders.
 *  This class is not thread safe - you need one encoder per thread.
 *  The encoder will save and recycle the internal objects, avoiding
 *  garbage.
 * 
 *  You can add extra characters that you want preserved, for example
 *  while encoding a URL you can add "/".
 *
 *  @author Costin Manolache
 */
public final class UEncoder {

    private final static Logger logger = LoggerUtils.getLogger();

    // Not static - the set may differ ( it's better than adding
    // an extra check for "/", "+", etc
    private BitSet safeChars=null;
    private C2BConverter c2b=null;
    private ByteChunk bb=null;

    private String encoding="UTF8";
    private static final int debug=0;
    
    public UEncoder() {
        initSafeChars();
    }

    public void setEncoding( String s ) {
        encoding=s;
    }

    public void addSafeCharacter( char c ) {
        safeChars.set( c );
    }

    /** URL Encode string, using a specified encoding.
     *  @param s string to be encoded
     *  @param enc character encoding, for chars &gt;%80 ( use UTF8 if not set,
     *         as recommended in RFCs)
     */
    public void urlEncode( Writer buf, String s )
            throws IOException {
        urlEncode(buf, s, false);
    }

    /** URL Encode string, using a specified encoding.
     *  @param s string to be encoded
     *  @param enc character encoding, for chars >%80 ( use UTF8 if not set,
     *         as recommended in RFCs)
     *  @param toHexUpperCase the hex string will be in upper case
     */
    public void urlEncode( Writer buf, String s, boolean toHexUpperCase )
            throws IOException
    {
        if( c2b==null ) {
            bb=new ByteChunk(16); // small enough.
            c2b=C2BConverter.getInstance( bb, encoding );
        }

        for (int i = 0; i < s.length(); i++) {
            int c = (int) s.charAt(i);
            if( safeChars.get( c ) ) {
                if( debug > 0 ) log("Safe: " + (char)c);
                buf.write((char)c);
            } else {
                if( debug > 0 ) log("Unsafe:  " + (char)c);
                c2b.convert( (char)c );
                
                // "surrogate" - UTF is _not_ 16 bit, but 21 !!!!
                // ( while UCS is 31 ). Amazing...
                if (c >= 0xD800 && c <= 0xDBFF) {
                    if ( (i+1) < s.length()) {
                        int d = (int) s.charAt(i+1);
                        if (d >= 0xDC00 && d <= 0xDFFF) {
                            if( debug > 0 ) log("Unsafe:  " + c);
                            c2b.convert( (char)d);
                            i++;
                        }
                    }
                }

                urlEncode( buf, bb.getBuffer(), bb.getOffset(),
                           bb.getLength(), toHexUpperCase );
                bb.recycle();
            }
        }
    }

    /**
     */
    public void urlEncode( Writer buf, byte bytes[], int off, int len)
            throws IOException {
        urlEncode(buf, bytes, off, len, false);
    }

    /**
     */
    public void urlEncode( Writer buf, byte bytes[], int off, int len, boolean toHexUpperCase )
            throws IOException
    {
        for( int j=off; j< len; j++ ) {
            buf.write( '%' );
            char ch = Character.forDigit((bytes[j] >> 4) & 0xF, 16);
        if (toHexUpperCase) {
            ch = Character.toUpperCase(ch);
        }
            if( debug > 0 ) log("Encode:  " + ch);
            buf.write(ch);
            ch = Character.forDigit(bytes[j] & 0xF, 16);
        if (toHexUpperCase) {
            ch = Character.toUpperCase(ch);
        }
            if( debug > 0 ) log("Encode:  " + ch);
            buf.write(ch);
        }
    }

    /**
     * Utility funtion to re-encode the URL.
     * Still has problems with charset, since UEncoder mostly
     * ignores it.
     * @param url
     */
    public String encodeURL(String url) {
        return encodeURL(url, false);
    }
    
    /**
     * Utility funtion to re-encode the URL.
     * Still has problems with charset, since UEncoder mostly
     * ignores it.
     * @param url
     * @param toHexUpperCase
     */
    public String encodeURL(String uri, boolean toHexUpperCase) {
        String outUri=null;
        try {
            // XXX optimize - recycle, etc
            CharArrayWriter out = new CharArrayWriter();
            urlEncode(out, uri, toHexUpperCase);
            outUri=out.toString();
        } catch (IOException iex) {
        }
        return outUri;
    }
    

    // -------------------- Internal implementation --------------------
    
    // 
    private void initSafeChars() {
        safeChars=new BitSet(128);
        int i;
        for (i = 'a'; i <= 'z'; i++) {
            safeChars.set(i);
        }
        for (i = 'A'; i <= 'Z'; i++) {
            safeChars.set(i);
        }
        for (i = '0'; i <= '9'; i++) {
            safeChars.set(i);
        }
        //safe
        safeChars.set('$');
        safeChars.set('-');
        safeChars.set('_');
        safeChars.set('.');

        // Dangerous: someone may treat this as " "
        // RFC1738 does allow it, it's not reserved
        //    safeChars.set('+');
        //extra
        safeChars.set('!');
        safeChars.set('*');
        safeChars.set('\'');
        safeChars.set('(');
        safeChars.set(')');
        safeChars.set(',');        
    }

    private static void log( String s ) {
        if (logger.isLoggable(Level.FINE)){
            logger.fine(s);
        }
    }
}
