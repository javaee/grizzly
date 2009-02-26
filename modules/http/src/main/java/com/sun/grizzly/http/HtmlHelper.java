/*
 * The contents of this file are subject to the terms 
 * of the Common Development and Distribution License 
 * (the License).  You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at 
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing 
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL 
 * Header Notice in each file and include the License file 
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.  
 * If applicable, add the following below the CDDL Header, 
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Date;

/**
 * Utility class used to generate HTML pages.
 *
 * @author Jean-Francois Arcand
 */
public class HtmlHelper{    
    
    private final static String CSS =
        "H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;} " +
        "H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} " +
        "H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} " +
        "BODY {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;} " +
        "B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;} " +
        "P {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}" +
        "A {color : black;}" +
        "HR {color : #525D76;}";

    /**
     * <code>CharBuffer</code> used to store the HTML response, containing
     * the headers and the body of the response.
     */
    private static CharBuffer reponseBuffer = CharBuffer.allocate(4096);
    

    /**
     * Encoder used to encode the HTML response
     */
    private static CharsetEncoder encoder =
                                          Charset.forName("UTF-8").newEncoder();

    /**
     * HTTP end line.
     */
    private static String NEWLINE = "\r\n";


    /**
     * HTTP OK header
     */
    public final static String OK = "HTTP/1.1 200 OK" + NEWLINE;
    

    /**
     * HTTP Bas Request
     */
    public final static String BAD_REQUEST 
        = "HTTP/1.1 400 Bad Request" + NEWLINE;
 
    
    /**
     * When Grizzlu has reached its connection-queue pool limits, an HTML
     * error pages will to be returned to the clients.
     *
     * @return A <code>ByteBuffer</code> containings the HTTP response.
     */
    public synchronized static ByteBuffer 
            getErrorPage(String message, String code) throws IOException {
        String body = prepareBody(message);
        reponseBuffer.clear();
        reponseBuffer.put(code);
        appendHeaderValue("Content-Type", "text/html");
        appendHeaderValue("Content-Length", body.getBytes().length + "");
        appendHeaderValue("Date", new Date().toString());
        appendHeaderValue("Connection", "Close");
        appendHeaderValue("Server", SelectorThread.SERVER_NAME);
        reponseBuffer.put(NEWLINE);
        reponseBuffer.put(body);
        reponseBuffer.flip();
        return encoder.encode(reponseBuffer);
    }

    
    /**
     * Utility to add headers to the HTTP response.
     */
    private static void appendHeaderValue(String name, String value) {
        reponseBuffer.put(name);
        reponseBuffer.put(": ");
        reponseBuffer.put(value);
        reponseBuffer.put(NEWLINE);
    }


    /**
     * Prepare the HTTP body containing the error messages.
     */
    private static String prepareBody(String message){
        StringBuffer sb = new StringBuffer();

        sb.append("<html><head><title>");
        sb.append(SelectorThread.SERVER_NAME);
        sb.append("</title>");
        sb.append("<style><!--");
        sb.append(CSS);
        sb.append("--></style> ");
        sb.append("</head><body>");
        sb.append("<h1>");
        sb.append(message);
        sb.append("</h1>");
        sb.append("<HR size=\"1\" noshade>");
        sb.append("<h3>").append(SelectorThread.SERVER_NAME)
            .append("</h3>");
        sb.append("</body></html>");
        return sb.toString();
    }

}
