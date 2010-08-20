/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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

package com.sun.grizzly.http.util;

import com.sun.grizzly.http.HttpResponsePacket;

/**
 * This <code>enum</code> encapsulates the HTTP response status and
 * reason phrases as defined by <code>RFC 2616</code>.
 * 
 * @since 2.0
 */
@SuppressWarnings({"UnusedDeclaration"})
public enum HttpStatus {
    
    CONINTUE_100(100, "Continue"),
    SWITCHING_PROTOCOLS_101(101, "Switching Protocols"),
    OK_200(200, "OK"),
    CREATED_201(201, "Created"),
    ACCEPTED_202(202, "Accepted"),
    NON_AUTHORATIVE_INFORMATION_203(203, "Not-Authoritative Information"),
    NO_CONTENT_204(204, "No Content"),
    RESET_CONTENT_205(205, "Reset Content"),
    PARTIAL_CONTENT_206(206, "Partial Content"),
    MULTIPLE_CHOICES_300(300, "Multiple Choices"),
    MOVED_PERMANENTLY_301(301, "Moved Permanently"),
    FOUND_302(302, "Found"),
    SEE_OTHER_303(303, "See Other"),
    NOT_MODIFIED_304(304, "Not Modified"),
    USE_PROXY_305(305, "Use Proxy"),
    TEMPORARY_REDIRECT_307(307, "Temporary Redirect"),
    BAD_REQUEST_400(400, "Bad Request"),
    UNAUTHORIZED_401(401, "Unauthorized"),
    PAYMENT_REQUIRED_402(402, "Payment Required"),
    FORBIDDEN_403(403, "Forbidden"),
    NOT_FOUND_404(404, "Not Found"),
    METHOD_NOT_ALLOWED_405(405, "Method Not Allowed"),
    NOT_ACCEPTABLE_406(406, "Not Acceptable"),
    PROXY_AUTHENTICATION_REQUIRED_407(407, "Proxy Authentication Required"),
    REQUEST_TIMEOUT_408(408, "Request Timeout"),
    CONFLICT_409(409, "Conflict"),
    GONE_410(410, "Gone"),
    LENGTH_REQUIRED_411(411, "Length Required"),
    PRECONDITION_FAILED_412(412, "Precondition Failed"),
    REQUEST_ENTITY_TOO_LARGE_413(413, "Request Entity Too Large"),
    REQUEST_URI_TOO_LONG_414(414, "Request-URI Too Long"),
    UNSUPPORTED_MEDIA_TYPE_415(415, "Unsupported Media Type"),
    REQUEST_RANGE_NOT_SATISFIABLE_416(416, "Request Range Not Satisfiable"),
    EXPECTATION_FAILED_417(417, "Expectation Failed"),
    INTERNAL_SERVER_ERROR_500(500, "Internal Server Error"),
    NOT_IMPLEMENTED_501(501, "Not Implemented"),
    BAD_GATEWAY_502(502, "Bad Gateway"),
    SERVICE_UNAVAILABLE_503(503, "Service Unavailable"),
    GATEWAY_TIMEOUT_504(504, "Gateway Timeout"),
    HTTP_VERSION_NOT_SUPPORTED_505(505, "HTTP Version Not Supported");


    // ------------------------------------------------------------ Constructors

    private final int status;
    private final String reasonPhrase;
    private final BufferChunk reasonPhraseBC;

    HttpStatus(final int status, final String reasonPhrase) {
        this.status = status;
        this.reasonPhrase = reasonPhrase;
        this.reasonPhraseBC = new BufferChunk();
        reasonPhraseBC.setString(reasonPhrase);
        reasonPhraseBC.lock();


    }


    // ---------------------------------------------------------- Public Methods


    /**
     * @return the <code>int</code> status code.
     */
    public int getStatusCode() {
        return status;
    }

    /**
     * @return the {@link BufferChunk} containing the reason phrase as
     *  defined by <code>RFC 2616</code>.
     */
    public BufferChunk getReasonPhraseBC() {
        return reasonPhraseBC;
    }

    /**
     * Sets the status and reason phrase on the specified response.
     * @param response the response to set the status and reason phrase on.
     */
    public void setValues(HttpResponsePacket response) {
        response.setStatus(status);
        response.setReasonPhrase(reasonPhrase);
    }

}
