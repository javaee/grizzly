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
 */

package org.glassfish.grizzly.http.server.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

/**
 * Utility class used to generate HTML pages.
 *
 * @author Jean-Francois Arcand
 */
public class HtmlHelper{

    private static final int MAX_STACK_ELEMENTS = 10;

    private final static String CSS =
            "div.header {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#003300;font-size:22px;-moz-border-radius-topleft: 10px;border-top-left-radius: 10px;-moz-border-radius-topright: 10px;border-top-right-radius: 10px;padding-left: 5px}" +
            "div.body {font-family:Tahoma,Arial,sans-serif;color:black;background-color:#FFFFCC;font-size:16px;padding-top:10px;padding-bottom:10px;padding-left:10px}" +
            "div.footer {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#666633;font-size:14px;-moz-border-radius-bottomleft: 10px;border-bottom-left-radius: 10px;-moz-border-radius-bottomright: 10px;border-bottom-right-radius: 10px;padding-left: 5px}" +
            "BODY {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;}" +
            "B {font-family:Tahoma,Arial,sans-serif;color:black;}" +
            "A {color : black;}" +
            "HR {color : #999966;}";

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
     *
     * @return A {@link ByteBuffer} containing the HTTP response.
     */
    public synchronized static ByteBuffer getErrorPage(String headerMessage,
                                                       String message,
                                                       String serverName)
    throws IOException {

        String body = prepareBody(headerMessage, message, serverName);
        reponseBuffer.clear();
        reponseBuffer.put(body);
        reponseBuffer.flip();
        return encoder.encode(reponseBuffer);

    }


    public synchronized static ByteBuffer getExceptionErrorPage(String message,
                                                                String serverName,
                                                                Throwable t)
    throws IOException {
        String body = prepareExceptionBody(message, serverName, t);
        reponseBuffer.clear();
        reponseBuffer.put(body);
        reponseBuffer.flip();
        return encoder.encode(reponseBuffer);

    }

    public static void writeTraceMessage(final Request request,
            final Response response) throws IOException {
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("message/http");
        final Writer writer = response.getWriter();
        writer.append(request.getMethod().toString()).append(' ')
                .append(request.getRequest().getRequestURIRef().getOriginalRequestURIBC().toString())
                .append(' ').append(request.getProtocol().getProtocolString())
                .append("\r\n");

        for (String headerName : request.getHeaderNames()) {
            for (String headerValue : request.getHeaders(headerName)) {
                writer.append(headerName).append(": ").append(headerValue).append("\r\n");
            }
        }
    }
    
    /**
     * Prepare the HTTP body containing the error messages.
     */
    private static String prepareBody(String headerMessage, String message, String serverName){
        final StringBuilder sb = new StringBuilder();

        sb.append("<html><head><title>");
        sb.append(serverName);
        sb.append("</title>");
        sb.append("<style><!--");
        sb.append(CSS);
        sb.append("--></style> ");
        sb.append("</head><body>");
        sb.append("<div class=\"header\">");
        sb.append(headerMessage);
        sb.append("</div>");
        sb.append("<div class=\"body\">");
        sb.append((message != null) ? message : "<HR size=\"1\" noshade>");
        sb.append("</div>");
        sb.append("<div class=\"footer\">").append(serverName).append("</div>");
        sb.append("</body></html>");
        return sb.toString();
    }


    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
    private static String prepareExceptionBody(String message,
                                               String serverName,
                                               Throwable t) {

        if (t == null) {
            throw new IllegalArgumentException();
        }
        Throwable rootCause = getRootCause(t);

        StackTraceElement[] elements = t.getStackTrace();
        StackTraceElement[] rootCauseElements = null;
        if (rootCause != null) {
            rootCauseElements = rootCause.getStackTrace();
        }
        StringBuilder tBuilder = new StringBuilder();
        formatStackElements(elements, tBuilder);
        StringBuilder rootBuilder = new StringBuilder();
        if (rootCause != null) {
            formatStackElements(rootCauseElements, rootBuilder);
        }
        String exMessage = t.getMessage();
        if (exMessage == null) {
            exMessage = t.toString();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><title>");
        sb.append(serverName);
        sb.append("</title>");
        sb.append("<style><!--");
        sb.append(CSS);
        sb.append("--></style> ");
        sb.append("</head><body>");
        sb.append("<div class=\"header\">");
        sb.append(message);
        sb.append("</div>");
        sb.append("<div class=\"body\">");
        sb.append("<b>").append(exMessage).append("</b>");
        sb.append("<pre>");
        sb.append(tBuilder.toString());
        sb.append("</pre>");
        if (rootCause != null) {
            sb.append("<b>Root Cause: ").append(rootCause.toString()).append("</b>");
            sb.append("<pre>");
            sb.append(rootBuilder.toString());
            sb.append("</pre>");
        }

        sb.append("Please see the log for more detail.");
        sb.append("</div>");
        sb.append("<div class=\"footer\">").append(serverName).append("</div>");
        sb.append("</body></html>");
        return sb.toString();
    }


    private static Throwable getRootCause(Throwable t) {

        Throwable rootCause = null;
        if (t.getCause() != null) {
            rootCause = t.getCause();
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
        }
        return rootCause;

    }


    private static void formatStackElements(StackTraceElement[] elements, StringBuilder builder) {

        final int maxLines = getMaxStackElementsToDisplay(elements);
        for (int i = 0; i < maxLines; i++) {
            builder.append((i + 1 > 9) ? "    " : "     ").append(i + 1).append(": ").append(elements[i].toString()).append('\n');
        }
        boolean ellipse = elements.length > MAX_STACK_ELEMENTS;
        if (ellipse) {
            builder.append("        ... ").append(elements.length - MAX_STACK_ELEMENTS).append(" more");
        }

    }


    private static int getMaxStackElementsToDisplay(StackTraceElement[] elements) {

        return ((elements.length > MAX_STACK_ELEMENTS) ? MAX_STACK_ELEMENTS : elements.length);
        
    }

}
