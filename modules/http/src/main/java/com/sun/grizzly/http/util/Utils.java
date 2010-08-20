/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
 *
 */

package com.sun.grizzly.http.util;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class.
 * 
 * @author Alexey Stashok
 */
public class Utils {

    private static ConcurrentHashMap<String, Charset> charsetAliasMap =
            new ConcurrentHashMap<String, Charset>();

    private static HashMap<Integer,BufferChunk> statusMessages =
            new HashMap<Integer,BufferChunk>();
    static {
        BufferChunk c = new BufferChunk();
        c.setString("Continue");
        statusMessages.put(100, c);
        c = new BufferChunk();
        c.setString("Switching Protocols");
        statusMessages.put(101, c);
        c = new BufferChunk();
        c.setString("OK");
        statusMessages.put(200, c);
        c = new BufferChunk();
        c.setString("Created");
        statusMessages.put(201, c);
        c = new BufferChunk();
        c.setString("Accepted");
        statusMessages.put(202, c);
        c = new BufferChunk();
        c.setString("Non-Authoritative Information");
        statusMessages.put(203, c);
        c = new BufferChunk();
        c.setString("No Content");
        statusMessages.put(204, c);
        c = new BufferChunk();
        c.setString("Reset Content");
        statusMessages.put(205, c);
        c = new BufferChunk();
        c.setString("Partial Content");
        statusMessages.put(205, c);
        c = new BufferChunk();
        c.setString("Multiple Choices");
        statusMessages.put(300, c);
        c = new BufferChunk();
        c.setString("Moved Permanently");
        statusMessages.put(301, c);
        c = new BufferChunk();
        c.setString("Found");
        statusMessages.put(302, c);
        c = new BufferChunk();
        c.setString("See Other");
        statusMessages.put(303, c);
        c = new BufferChunk();
        c.setString("Not Modified");
        statusMessages.put(304, c);
        c = new BufferChunk();
        c.setString("Use Proxy");
        statusMessages.put(305, c);
        c = new BufferChunk();
        c.setString("Temporary Redirect");
        statusMessages.put(307, c);
        c = new BufferChunk();
        c.setString("Bad Request");
        statusMessages.put(400, c);
        c = new BufferChunk();
        c.setString("Unauthorized");
        statusMessages.put(401, c);
        c = new BufferChunk();
        c.setString("Payment Required");
        statusMessages.put(402, c);
        c = new BufferChunk();
        c.setString("Forbidden");
        statusMessages.put(403, c);
        c = new BufferChunk();
        c.setString("Not Found");
        statusMessages.put(404, c);
        c = new BufferChunk();
        c.setString("Method Not Allowed");
        statusMessages.put(405, c);
        c = new BufferChunk();
        c.setString("Not Acceptable");
        statusMessages.put(406, c);
        c = new BufferChunk();
        c.setString("Proxy Authentication Required");
        statusMessages.put(407, c);
        c = new BufferChunk();
        c.setString("Request Timeout");
        statusMessages.put(408, c);
        c = new BufferChunk();
        c.setString("Conflict");
        statusMessages.put(409, c);
        c = new BufferChunk();
        c.setString("Gone");
        statusMessages.put(410, c);
        c = new BufferChunk();
        c.setString("Length Required");
        statusMessages.put(411, c);
        c = new BufferChunk();
        c.setString("Precondition Failed");
        statusMessages.put(412, c);
        c = new BufferChunk();
        c.setString("Request Entity Too Large");
        statusMessages.put(413, c);
        c = new BufferChunk();
        c.setString("Request-URI Too Long");
        statusMessages.put(414, c);
        c = new BufferChunk();
        c.setString("Unsupported Media Type");
        statusMessages.put(415, c);
        c = new BufferChunk();
        c.setString("Request Range Not Satisfiable");
        statusMessages.put(416, c);
        c = new BufferChunk();
        c.setString("Expectation Failed");
        statusMessages.put(417, c);
        c = new BufferChunk();
        c.setString("Internal Server Error");
        statusMessages.put(500, c);
        c = new BufferChunk();
        c.setString("Not Implemented");
        statusMessages.put(501, c);
        c = new BufferChunk();
        c.setString("Bad Gateway");
        statusMessages.put(502, c);
        c = new BufferChunk();
        c.setString("Service Unavailable");
        statusMessages.put(503, c);
        c = new BufferChunk();
        c.setString("Gateway Timeout");
        statusMessages.put(504, c);
        c = new BufferChunk();
        c.setString("HTTP Version Not Supported");
        statusMessages.put(505, c);
        
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * Lookup a {@link Charset} by name.
     * Fixes Charset concurrency issue (http://paul.vox.com/library/post/the-mysteries-of-java-character-set-performance.html)
     *
     * @param charsetName
     * @return {@link Charset}
     */
    public static Charset lookupCharset(String charsetName) {
        Charset charset = charsetAliasMap.get(charsetName);
        if (charset == null) {
            charset = Charset.forName(charsetName);
            charsetAliasMap.putIfAbsent(charsetName, charset);
        }

        return charset;
    }


    /**
     * @param httpStatus HTTP status code
     * @return the standard HTTP message associated with the specified
     *  status code.  If there is no message for the specified <code>httpStatus</code>,
     *  <code>null</code> shall be returned.
     */
    public static BufferChunk getHttpStatusMessage(final int httpStatus) {

        return statusMessages.get(httpStatus);
        
    }

}
