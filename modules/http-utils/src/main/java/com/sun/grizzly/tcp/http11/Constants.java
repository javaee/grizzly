 

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

package com.sun.grizzly.tcp.http11;


/**
 * Constants.
 *
 * @author Remy Maucherat
 */
public final class Constants {


    // -------------------------------------------------------------- Constants


    /**
     * Package name.
     */
    public static final String Package = "com.sun.grizzly.tcp.http11";

    public static final int DEFAULT_CONNECTION_LINGER = -1;
    public static final int DEFAULT_CONNECTION_TIMEOUT = 60000;
    public static final int DEFAULT_CONNECTION_UPLOAD_TIMEOUT = 300000;
    public static final int DEFAULT_SERVER_SOCKET_TIMEOUT = 0;
    public static final boolean DEFAULT_TCP_NO_DELAY = true;
    
    /**
     * Server string.
     */
 
    /* S1AS8PE 4929847, 5022949
    public static final String SERVER = "Apache-Coyote/1.1";
     */

    /**
     * CR.
     */
    public static final byte CR = (byte) '\r';


    /**
     * LF.
     */
    public static final byte LF = (byte) '\n';


    /**
     * SP.
     */
    public static final byte SP = (byte) ' ';


    /**
     * HT.
     */
    public static final byte HT = (byte) '\t';


    /**
     * COLON.
     */
    public static final byte COLON = (byte) ':';


    /**
     * SEMI_COLON.
     */
    public static final byte SEMI_COLON = (byte) ';';


    /**
     * 'A'.
     */
    public static final byte A = (byte) 'A';


    /**
     * 'a'.
     */
    public static final byte a = (byte) 'a';


    /**
     * 'Z'.
     */
    public static final byte Z = (byte) 'Z';


    /**
     * '?'.
     */
    public static final byte QUESTION = (byte) '?';


    /**
     * Lower case offset.
     */
    public static final byte LC_OFFSET = A - a;


    /**
     * Default HTTP header buffer size.
     */
    public static final int DEFAULT_HTTP_HEADER_BUFFER_SIZE = 48 * 1024;


    /**
     * CRLF.
     */
    public static final String CRLF = "\r\n";


    /**
     * CRLF bytes.
     */
    public static final byte[] CRLF_BYTES = {(byte) '\r', (byte) '\n'};


    /**
     * Colon bytes.
     */
    public static final byte[] COLON_BYTES = {(byte) ':', (byte) ' '};


    /**
     * Close bytes.
     */
    public static final byte[] CLOSE_BYTES = {
        (byte) 'c',
        (byte) 'l',
        (byte) 'o',
        (byte) 's',
        (byte) 'e'
    };


    /**
     * Keep-alive bytes.
     */
    public static final byte[] KEEPALIVE_BYTES = {
        (byte) 'k',
        (byte) 'e',
        (byte) 'e',
        (byte) 'p',
        (byte) '-',
        (byte) 'a',
        (byte) 'l',
        (byte) 'i',
        (byte) 'v',
        (byte) 'e'
    };


    /**
     * Identity filters (input and output).
     */
    public static final int IDENTITY_FILTER = 0;


    /**
     * Chunked filters (input and output).
     */
    public static final int CHUNKED_FILTER = 1;


    /**
     * Void filters (input and output).
     */
    public static final int VOID_FILTER = 2;


    /**
     * GZIP filter (output).
     */
    public static final int GZIP_FILTER = 3;


    /**
     * Buffered filter (input)
     */
    public static final int BUFFERED_FILTER = 3;


    /**
     * HTTP/1.0.
     */
    public static final String HTTP_10 = "HTTP/1.0";


    /**
     * HTTP/1.1.
     */
    public static final String HTTP_11 = "HTTP/1.1";


    /**
     * GET.
     */
    public static final String GET = "GET";


    /**
     * HEAD.
     */
    public static final String HEAD = "HEAD";


    /**
     * POST.
     */
    public static final String POST = "POST";


    /**
     * Ack string when pipelining HTTP requests.
     */
    public static final byte[] ACK_BYTES = {
        (byte) 'H',
        (byte) 'T',
        (byte) 'T',
        (byte) 'P',
        (byte) '/',
        (byte) '1',
        (byte) '.',
        (byte) '1',
        (byte) ' ',
        (byte) '1',
        (byte) '0',
        (byte) '0',
        (byte) ' ',
        (byte) 'C',
        (byte) 'o',
        (byte) 'n',
        (byte) 't',
        (byte) 'i',
        (byte) 'n',
        (byte) 'u',
        (byte) 'e',
        (byte) '\r',
        (byte) '\n',
        (byte) '\r',
        (byte) '\n'
    };

    public static final int PROCESSOR_IDLE = 0;
    public static final int PROCESSOR_ACTIVE = 1;

    /**
     * Default header names.
     */
    public static final String AUTHORIZATION_HEADER = "authorization";

    /**
     * SSL Certificate Request Attributite.
     */
    public static final String SSL_CERTIFICATE_ATTR = "org.apache.coyote.request.X509Certificate";

    /**
     * Security flag.
     */
    public static final boolean SECURITY = 
        (System.getSecurityManager() != null);

    
    // S1AS 4703023
    public static final int DEFAULT_MAX_DISPATCH_DEPTH = 20;

    
    // START SJSAS 6328909
    /**
     * The default response-type
     */
    public final static String DEFAULT_RESPONSE_TYPE = 
            "text/html; charset=iso-8859-1";


    /**
     * The forced response-type
     */
    public final static String FORCED_RESPONSE_TYPE = 
           "text/html; charset=iso-8859-1";
    // END SJSAS 6328909


    // START SJSAS 6337561
    public final static String PROXY_JROUTE = "proxy-jroute";
    // END SJSAS 6337561

    // START SJSAS 6346226
    public final static String JROUTE_COOKIE = "JROUTE";
    // END SJSAS 6346226
}
