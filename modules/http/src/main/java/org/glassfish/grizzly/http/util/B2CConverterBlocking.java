/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.util;


import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.Grizzly;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Efficient conversion of bytes  to character .
 *
 *  This uses the standard JDK mechanism - a reader - but provides mechanisms
 *  to recycle all the objects that are used. It is compatible with JDK1.1
 *  and up,
 *  ( nio is better, but it's not available even in 1.2 or 1.3 )
 *
 *  Not used in the current code, the performance gain is not very big
 *  in the current case ( since String is created anyway ), but it will
 *  be used in a later version or after the remaining optimizations.
 */
public class B2CConverterBlocking {
    /**
     * Default Logger.
     */
    private final static Logger logger = Grizzly.logger(B2CConverterBlocking.class);

    private IntermediateInputStream iis;
    private ReadConverter conv;
    private String encoding;

    protected B2CConverterBlocking() {
    }

    /** Create a converter, with bytes going to a byte buffer
     */
    public B2CConverterBlocking(String encoding)
	throws IOException
    {
	this.encoding=encoding;
	reset();
    }


    /** Reset the internal state, empty the buffers.
     *  The encoding remain in effect, the internal buffers remain allocated.
     */
    public  void recycle() {
	conv.recycle();
    }

    static final int BUFFER_SIZE=8192;
    final char[] result=new char[BUFFER_SIZE];

    /** Convert a buffer of bytes into a chars
     * @deprecated
     */
    public  void convert( ByteChunk bb, CharChunk cb )
	throws IOException
    {
	// Set the ByteChunk as input to the Intermediate reader
        convert(bb, cb, cb.getBuffer().length - cb.getEnd());
    }

    public void convert( ByteChunk bb, CharChunk cb, int limit)
        throws IOException
    {
	iis.setByteChunk( bb );
        int debug = 0;
        try {
	    // read from the reader
            int bbLengthBeforeRead;
	    while( limit > 0 ) { // conv.ready() ) {
                int size = limit < BUFFER_SIZE ? limit : BUFFER_SIZE;
                bbLengthBeforeRead = bb.getLength();
		int cnt=conv.read( result, 0, size );
		if( cnt <= 0 ) {
		    // End of stream ! - we may be in a bad state
		    if( debug >0)
			log( "EOF" );
		    return;
		}
		if( debug > 1 )
		    log("Converted: " + new String( result, 0, cnt ));
		cb.append( result, 0, cnt );
                limit = limit - (bbLengthBeforeRead - bb.getLength());
	    }
	} catch( IOException ex) {
	    if( debug >0)
		log( "Resetting the converter " + ex.toString() );
	    reset();
	    throw ex;
	}
    }

    // START CR 6309511
    /**
     * Character conversion of a US-ASCII MessageBytes.
     */
    public static void convertASCII(MessageBytes mb) {

        // This is of course only meaningful for bytes
        if (mb.getType() != MessageBytes.T_BYTES)
            return;

        ByteChunk bc = mb.getByteChunk();
        CharChunk cc = mb.getCharChunk();
        int length = bc.getLength();
        cc.allocate(length, -1);

        // Default encoding: fast conversion
        byte[] bbuf = bc.getBuffer();
        char[] cbuf = cc.getBuffer();
        int start = bc.getStart();
        for (int i = 0; i < length; i++) {
            cbuf[i] = (char) (bbuf[i + start] & 0xff);
        }
        mb.setChars(cbuf, 0, length);

     }
    // END CR 6309511

    public void reset()
	throws IOException
    {
	// destroy the reader/iis
	iis=new IntermediateInputStream();
	conv=new ReadConverter( iis, Charsets.lookupCharset(encoding) );
    }

    void log( String s ) {
        if (logger.isLoggable(Level.FINEST))
	    logger.log(Level.FINEST,"B2CConverter: " + s );
    }

    // -------------------- Not used - the speed improvement is quite small

    /*
    private Hashtable decoders;
    public static final boolean useNewString=false;
    public static final boolean useSpecialDecoders=true;
    private UTF8Decoder utfD;
    // private char[] conversionBuff;
    CharChunk conversionBuf;


    private  static String decodeString(ByteChunk mb, String enc)
	throws IOException
    {
	byte buff=mb.getBuffer();
	int start=mb.getStart();
	int end=mb.getEnd();
	if( useNewString ) {
	    if( enc==null) enc="UTF8";
	    return new String( buff, start, end-start, enc );
	}
	B2CConverter b2c=null;
	if( useSpecialDecoders &&
	    (enc==null || "UTF8".equalsIgnoreCase(enc))) {
	    if( utfD==null ) utfD=new UTF8Decoder();
	    b2c=utfD;
	}
	if(decoders == null ) decoders=new Hashtable();
	if( enc==null ) enc="UTF8";
	b2c=(B2CConverter)decoders.get( enc );
	if( b2c==null ) {
	    if( useSpecialDecoders ) {
		if( "UTF8".equalsIgnoreCase( enc ) ) {
		    b2c=new UTF8Decoder();
		}
	    }
	    if( b2c==null )
		b2c=new B2CConverter( enc );
	    decoders.put( enc, b2c );
	}
	if( conversionBuf==null ) conversionBuf=new CharChunk(1024);

	try {
	    conversionBuf.recycle();
	    b2c.convert( this, conversionBuf );
	    //System.out.println("XXX 1 " + conversionBuf );
	    return conversionBuf.toString();
	} catch( IOException ex ) {
	    ex.printStackTrace();
	    return null;
	}
    }

    */
}

// -------------------- Private implementation --------------------



/**
 *
 */
final class ReadConverter extends InputStreamReader {

    /** Create a converter.
     */
    public ReadConverter( IntermediateInputStream in, Charset charset )
	throws UnsupportedEncodingException
    {
	super( in, charset );
    }

    /** Overriden - will do nothing but reset internal state.
     */
    public  final void close() throws IOException {
	// NOTHING
	// Calling super.close() would reset out and cb.
    }

    public  final int read(char cbuf[], int off, int len)
	throws IOException
    {
	// will do the conversion and call write on the output stream
	return super.read( cbuf, off, len );
    }

    /** Reset the buffer
     */
    public  final void recycle() {
        try {
            // Must clear super's buffer.
            while (ready()) {
                // InputStreamReader#skip(long) will allocate buffer to skip.
                read();
            }
        } catch(IOException ignore){
        }
    }
}


/** Special output stream where close() is overriden, so super.close()
    is never called.

    This allows recycling. It can also be disabled, so callbacks will
    not be called if recycling the converter and if data was not flushed.
*/
final class IntermediateInputStream extends InputStream {
    ByteChunk bc = null;
    boolean initialized = false;

    public IntermediateInputStream() {
    }

    public  final void close() throws IOException {
	// shouldn't be called - we filter it out in writer
	throw new IOException("close() called - shouldn't happen ");
    }

    public  final  int read(byte cbuf[], int off, int len) throws IOException {
        if (!initialized) return -1;
        return bc.substract(cbuf, off, len);
    }

    public  final int read() throws IOException {
        if (!initialized) return -1;
        return bc.substract();
    }

    public int available() throws IOException {
        if (!initialized)  return 0;
        return bc.getLength();
    }

    // -------------------- Internal methods --------------------

    void setByteChunk( ByteChunk mb ) {
        initialized = (mb != null);
        bc = mb;
    }

}
