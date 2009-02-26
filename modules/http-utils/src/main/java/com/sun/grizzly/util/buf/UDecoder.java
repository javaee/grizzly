

/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * glassfish/bootstrap/legal/CDDLv1.0.txt or
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * glassfish/bootstrap/legal/CDDLv1.0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 *
 * Portions Copyright Apache Software Foundation.
 */ 

package com.sun.grizzly.util.buf;


import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 
 *  All URL decoding happens here. This way we can reuse, review, optimize
 *  without adding complexity to the buffers.
 *
 *  The conversion will modify the original buffer.
 * 
 *  @author Costin Manolache
 */
public final class UDecoder {
    
    /**
     * Default Logger.
     */
    private final static Logger logger = Logger.getLogger("grizzly");

    protected static final boolean ALLOW_ENCODED_SLASH = 
        Boolean.valueOf(System.getProperty("com.sun.grizzly.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "false")).booleanValue();


    public UDecoder() 
    {
    }

    /** URLDecode, will modify the source.  Includes converting
     *  '+' to ' '.
     */
    public void convert( ByteChunk mb )
        throws IOException
    {
        convert(mb, true);
    }

    /** URLDecode, will modify the source.
     */
    public void convert( ByteChunk mb, boolean query )
	throws IOException
    {
	int start=mb.getOffset();
	byte buff[]=mb.getBytes();
	int end=mb.getEnd();

	int idx= mb.indexOf( buff, start, end, '%' );
        int idx2=-1;
        if( query )
            idx2= mb.indexOf( buff, start, end, '+' );
	if( idx<0 && idx2<0 ) {
	    return;
	}

	// idx will be the smallest positive inxes ( first % or + )
	if( idx2 >= 0 && idx2 < idx ) idx=idx2;
	if( idx < 0 ) idx=idx2;

    boolean noSlash = !(ALLOW_ENCODED_SLASH || query);

	for( int j=idx; j<end; j++, idx++ ) {
	    if( buff[ j ] == '+' && query) {
		buff[idx]= (byte)' ' ;
	    } else if( buff[ j ] != '%' ) {
		buff[idx]= buff[j];
	    } else {
		// read next 2 digits
		if( j+2 >= end ) {
		    throw new CharConversionException("EOF");
		}
		byte b1= buff[j+1];
		byte b2=buff[j+2];
		if( !isHexDigit( b1 ) || ! isHexDigit(b2 ))
		    throw new CharConversionException( "isHexDigit");
		
		j+=2;
		int res=x2c( b1, b2 );
        if (noSlash && (res == '/')) {
            throw new CharConversionException("noSlash");
        }
		buff[idx]=(byte)res;
	    }
	}

	mb.setEnd( idx );
	
	return;
    }

    // -------------------- Additional methods --------------------
    // XXX What do we do about charset ????

    /** In-buffer processing - the buffer will be modified
     *  Includes converting  '+' to ' '.
     */
    public void convert( CharChunk mb )
	throws IOException
    {
        convert(mb, true);
    }

    /** In-buffer processing - the buffer will be modified
     */
    public void convert( CharChunk mb, boolean query )
	throws IOException
    {
	//	log( "Converting a char chunk ");
	int start=mb.getOffset();
	char buff[]=mb.getBuffer();
	int cend=mb.getEnd();

	int idx= mb.indexOf( buff, start, cend, '%' );
        int idx2=-1;
        if( query )
            idx2= mb.indexOf( buff, start, cend, '+' );
	if( idx<0 && idx2<0 ) {
	    return;
	}
	
	if( idx2 >= 0 && idx2 < idx ) idx=idx2; 
	if( idx < 0 ) idx=idx2;

	for( int j=idx; j<cend; j++, idx++ ) {
	    if( buff[ j ] == '+' && query ) {
		buff[idx]=( ' ' );
	    } else if( buff[ j ] != '%' ) {
		buff[idx]=buff[j];
	    } else {
		// read next 2 digits
		if( j+2 >= cend ) {
		    // invalid
		    throw new CharConversionException("EOF");
		}
		char b1= buff[j+1];
		char b2=buff[j+2];
		if( !isHexDigit( b1 ) || ! isHexDigit(b2 ))
		    throw new CharConversionException("isHexDigit");
		
		j+=2;
		int res=x2c( b1, b2 );
		buff[idx]=(char)res;
	    }
	}
	mb.setEnd( idx );
    }

    /** URLDecode, will modify the source
     *  Includes converting  '+' to ' '.
     */
    public void convert(MessageBytes mb)
	throws IOException
    {
        convert(mb, true);
    }

    /** URLDecode, will modify the source
     */
    public void convert(MessageBytes mb, boolean query)
	throws IOException
    {
	
	switch (mb.getType()) {
	case MessageBytes.T_STR:
	    String strValue=mb.toString();
	    if( strValue==null ) return;
	    mb.setString( convert( strValue, query ));
	    break;
	case MessageBytes.T_CHARS:
	    CharChunk charC=mb.getCharChunk();
	    convert( charC, query );
	    break;
	case MessageBytes.T_BYTES:
	    ByteChunk bytesC=mb.getByteChunk();
	    convert( bytesC, query );
	    break;
	}
    }

    // XXX Old code, needs to be replaced !!!!
    // 
    public final String convert(String str)
    {
        return convert(str, true);
    }

    public final String convert(String str, boolean query)
    {
        if (str == null)  return  null;
	
	if( (!query || str.indexOf( '+' ) < 0) && str.indexOf( '%' ) < 0 )
	    return str;
	
        StringBuffer dec = new StringBuffer();    // decoded string output
        int strPos = 0;
        int strLen = str.length();

        dec.ensureCapacity(str.length());
        while (strPos < strLen) {
            int laPos;        // lookahead position

            // look ahead to next URLencoded metacharacter, if any
            for (laPos = strPos; laPos < strLen; laPos++) {
                char laChar = str.charAt(laPos);
                if ((laChar == '+' && query) || (laChar == '%')) {
                    break;
                }
            }

            // if there were non-metacharacters, copy them all as a block
            if (laPos > strPos) {
                dec.append(str.substring(strPos,laPos));
                strPos = laPos;
            }

            // shortcut out of here if we're at the end of the string
            if (strPos >= strLen) {
                break;
            }

            // process next metacharacter
            char metaChar = str.charAt(strPos);
            if (metaChar == '+') {
                dec.append(' ');
                strPos++;
                continue;
            } else if (metaChar == '%') {
		// We throw the original exception - the super will deal with
		// it
		//                try {
		dec.append((char)Integer.
			   parseInt(str.substring(strPos + 1, strPos + 3),16));
                strPos += 3;
            }
        }

        return dec.toString();
    }



    private static boolean isHexDigit( int c ) {
	return ( ( c>='0' && c<='9' ) ||
		 ( c>='a' && c<='f' ) ||
		 ( c>='A' && c<='F' ));
    }
    
    private static int x2c( byte b1, byte b2 ) {
	int digit= (b1>='A') ? ( (b1 & 0xDF)-'A') + 10 :
	    (b1 -'0');
	digit*=16;
	digit +=(b2>='A') ? ( (b2 & 0xDF)-'A') + 10 :
	    (b2 -'0');
	return digit;
    }

    private static int x2c( char b1, char b2 ) {
	int digit= (b1>='A') ? ( (b1 & 0xDF)-'A') + 10 :
	    (b1 -'0');
	digit*=16;
	digit +=(b2>='A') ? ( (b2 & 0xDF)-'A') + 10 :
	    (b2 -'0');
	return digit;
    }

    private final static int debug=0;
    private static void log( String s ) {
        if (logger.isLoggable(Level.FINEST))
	    logger.log(Level.FINEST,"URLDecoder: " + s );
    }

}
