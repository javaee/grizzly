/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.http.server.ErrorPageGenerator;
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
     * Generate and send an error page for the given HTTP response status.
     * Unlike {@link #setErrorAndSendErrorPage(org.glassfish.grizzly.http.server.Request, org.glassfish.grizzly.http.server.Response, org.glassfish.grizzly.http.server.ErrorPageGenerator, int, java.lang.String, java.lang.String, java.lang.Throwable)},
     * this method doesn't change the {@link Response} status code and reason phrase.
     * 
     * @param request
     * @param response
     * @param generator
     * @param status
     * @param reasonPhrase
     * @param description
     * @param exception
     * 
     * @throws IOException 
     */
    public static void sendErrorPage(
            final Request request, final Response response,
            final ErrorPageGenerator generator,
            final int status, final String reasonPhrase,
            final String description, final Throwable exception) throws IOException {
        
        if (generator != null && !response.isCommitted() &&
                response.getOutputBuffer().getBufferedDataSize() == 0) {
            final String errorPage = generator.generate(request, status,
                    reasonPhrase, description, exception);
            
            final Writer writer = response.getWriter();
            
            if (errorPage != null) {
                if (!response.getResponse().isContentTypeSet()) {
                    response.setContentType("text/html");
                }
                
                writer.write(errorPage);
            }
            writer.close();
        }
    }
    
    /**
     * Generate and send an error page for the given HTTP response status.
     * Unlike {@link #setErrorAndSendErrorPage(org.glassfish.grizzly.http.server.Request, org.glassfish.grizzly.http.server.Response, org.glassfish.grizzly.http.server.ErrorPageGenerator, int, java.lang.String, java.lang.String, java.lang.Throwable)},
     * this method does change the {@link Response} status code and reason phrase.
     * 
     * @param request
     * @param response
     * @param generator
     * @param status
     * @param reasonPhrase
     * @param description
     * @param exception
     * 
     * @throws IOException 
     */
    public static void setErrorAndSendErrorPage(
            final Request request, final Response response,
            final ErrorPageGenerator generator,
            final int status, final String reasonPhrase,
            final String description, final Throwable exception) throws IOException {
        
        response.setStatus(status, reasonPhrase);
        
        if (generator != null && !response.isCommitted() &&
                response.getOutputBuffer().getBufferedDataSize() == 0) {
            final String errorPage = generator.generate(request, status,
                    reasonPhrase, description, exception);
            
            final Writer writer = response.getWriter();
            
            if (errorPage != null) {
                if (!response.getResponse().isContentTypeSet()) {
                    response.setContentType("text/html");
                }
                
                writer.write(errorPage);
            }
            writer.close();
        }
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
     *
     * @return A {@link String} containing the HTTP response.
     */
    public static String getErrorPage(String headerMessage,
            String message, String serverName) {
        return prepareBody(headerMessage, message, serverName);
    }


    public static String getExceptionErrorPage(String headerMessage,
            String message, String serverName, Throwable t) {
        return prepareExceptionBody(headerMessage, message, serverName, t);
    }

    /**
     * Prepare the HTTP body containing the error messages.
     */
    private static String prepareBody(String headerMessage, String message,
            String serverName) {
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
    private static String prepareExceptionBody(String headerMessage,
            String message, String serverName, Throwable t) {

        if (t == null) {
            return prepareBody(headerMessage, message, serverName);
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
        
        final String exMessage = t.getMessage() != null ?
                t.getMessage() : t.toString();
        
        StringBuilder sb = new StringBuilder();
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
