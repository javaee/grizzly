 

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

/**
 * This class implements some basic ASCII character handling functions.
 *
 * @author dac@eng.sun.com
 * @author James Todd [gonzo@eng.sun.com]
 */
public final class Ascii {
    /*
     * Character translation tables.
     */

    private static final byte[] toUpper = new byte[256];
    private static final byte[] toLower = new byte[256];

    /*
     * Character type tables.
     */

    private static final boolean[] isAlpha = new boolean[256];
    private static final boolean[] isUpper = new boolean[256];
    private static final boolean[] isLower = new boolean[256];
    private static final boolean[] isWhite = new boolean[256];
    private static final boolean[] isDigit = new boolean[256];

    /*
     * Initialize character translation and type tables.
     */

    static {
	for (int i = 0; i < 256; i++) {
	    toUpper[i] = (byte)i;
	    toLower[i] = (byte)i;
	}

	for (int lc = 'a'; lc <= 'z'; lc++) {
	    int uc = lc + 'A' - 'a';

	    toUpper[lc] = (byte)uc;
	    toLower[uc] = (byte)lc;
	    isAlpha[lc] = true;
	    isAlpha[uc] = true;
	    isLower[lc] = true;
	    isUpper[uc] = true;
	}

	isWhite[ ' '] = true;
	isWhite['\t'] = true;
	isWhite['\r'] = true;
	isWhite['\n'] = true;
	isWhite['\f'] = true;
	isWhite['\b'] = true;

	for (int d = '0'; d <= '9'; d++) {
	    isDigit[d] = true;
	}
    }

    /**
     * Returns the upper case equivalent of the specified ASCII character.
     */

    public static int toUpper(int c) {
	return toUpper[c & 0xff] & 0xff;
    }

    /**
     * Returns the lower case equivalent of the specified ASCII character.
     */

    public static int toLower(int c) {
	return toLower[c & 0xff] & 0xff;
    }

    /**
     * Returns true if the specified ASCII character is upper or lower case.
     */

    public static boolean isAlpha(int c) {
	return isAlpha[c & 0xff];
    }

    /**
     * Returns true if the specified ASCII character is upper case.
     */

    public static boolean isUpper(int c) {
	return isUpper[c & 0xff];
    }

    /**
     * Returns true if the specified ASCII character is lower case.
     */

    public static boolean isLower(int c) {
	return isLower[c & 0xff];
    }

    /**
     * Returns true if the specified ASCII character is white space.
     */

    public static boolean isWhite(int c) {
	return isWhite[c & 0xff];
    }

    /**
     * Returns true if the specified ASCII character is a digit.
     */

    public static boolean isDigit(int c) {
	return isDigit[c & 0xff];
    }

    /**
     * Parses an unsigned integer from the specified subarray of bytes.
     * @param b the bytes to parse
     * @param off the start offset of the bytes
     * @param len the length of the bytes
     * @exception NumberFormatException if the integer format was invalid
     */
    public static int parseInt(byte[] b, int off, int len)
	throws NumberFormatException
    {
        int c;

	if (b == null || len <= 0 || !isDigit(c = b[off++])) {
	    throw new NumberFormatException();
	}

	int n = c - '0';

	while (--len > 0) {
	    if (!isDigit(c = b[off++])) {
		throw new NumberFormatException();
	    }
	    n = n * 10 + c - '0';
	}

	return n;
    }

    public static int parseInt(char[] b, int off, int len)
	throws NumberFormatException
    {
        int c;

	if (b == null || len <= 0 || !isDigit(c = b[off++])) {
	    throw new NumberFormatException();
	}

	int n = c - '0';

	while (--len > 0) {
	    if (!isDigit(c = b[off++])) {
		throw new NumberFormatException();
	    }
	    n = n * 10 + c - '0';
	}

	return n;
    }

    /**
     * Parses an unsigned long from the specified subarray of bytes.
     * @param b the bytes to parse
     * @param off the start offset of the bytes
     * @param len the length of the bytes
     * @exception NumberFormatException if the long format was invalid
     */
    public static long parseLong(byte[] b, int off, int len)
        throws NumberFormatException
    {
        int c;

        if (b == null || len <= 0 || !isDigit(c = b[off++])) {
            throw new NumberFormatException();
        }

        long n = c - '0';
        long m;
        
        while (--len > 0) {
            if (!isDigit(c = b[off++])) {
                throw new NumberFormatException();
            }
            m = n * 10 + c - '0';

            if (m < n) {
                // Overflow
                throw new NumberFormatException();
            } else {
                n = m;
            }
        }

        return n;
    }

    public static long parseLong(char[] b, int off, int len)
        throws NumberFormatException
    {
        int c;

        if (b == null || len <= 0 || !isDigit(c = b[off++])) {
            throw new NumberFormatException();
        }

        long n = c - '0';
        long m;

        while (--len > 0) {
            if (!isDigit(c = b[off++])) {
                throw new NumberFormatException();
            }
            m = n * 10 + c - '0';

            if (m < n) {
                // Overflow
                throw new NumberFormatException();
            } else {
                n = m;
            }
        }

        return n;
    }

}
