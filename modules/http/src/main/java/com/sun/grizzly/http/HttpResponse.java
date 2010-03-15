/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2008 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.http;

import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.MimeHeaders;



/**
 * Response object.
 * 
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Harish Prabandham
 * @author Hans Bergsten <hans@gefionsoftware.com>
 * @author Remy Maucherat
 */
public class HttpResponse extends HttpHeader {
    public static final int NON_PARSED_STATUS = Integer.MIN_VALUE;
    
    // ----------------------------------------------------- Instance Variables

    /**
     * Status code.
     */
    protected int parsedStatusInt = NON_PARSED_STATUS;
    protected BufferChunk statusBC = BufferChunk.newInstance();


    /**
     * Status message.
     */
    private BufferChunk reasonPhraseBC = BufferChunk.newInstance();

    public static Builder builder() {
        return new Builder();
    }

    // ----------------------------------------------------------- Constructors
    protected HttpResponse() {
    }

    // ------------------------------------------------------------- Properties
    public MimeHeaders getMimeHeaders() {
        return headers;
    }


    // -------------------- State --------------------

    public BufferChunk getStatusBC() {
        return statusBC;
    }

    public int getStatus() {
        if (parsedStatusInt == NON_PARSED_STATUS) {
            parsedStatusInt = Integer.parseInt(statusBC.toString());
        }

        return parsedStatusInt;
    }
    
    /** 
     * Set the response status 
     */ 
    public void setStatus(int status) {
        parsedStatusInt = status;
        statusBC.setString(Integer.toString(status));
    }


    public BufferChunk getReasonPhraseBC() {
        return reasonPhraseBC;
    }

    /**
     * Get the status message.
     */
    public String getReasonPhrase() {
        return reasonPhraseBC.toString();
    }


    /**
     * Set the status message.
     */
    public void setReasonPhrase(String message) {
        reasonPhraseBC.setString(message);
    }

    // --------------------
    
    @Override
    public void recycle() {
        super.recycle();
        statusBC.recycle();
        reasonPhraseBC.recycle();
    }

    @Override
    public final boolean isRequest() {
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(256);
        sb.append("HttpResponse (status=").append(getStatus())
                .append(" reason=").append(getReasonPhrase())
                .append(" protocol=").append(getProtocol())
                .append(" content-length=").append(getContentLength())
                .append(" headers=").append(getHeaders())
                .append(')');
        
        return sb.toString();
    }

    public static class Builder extends HttpHeader.Builder<Builder> {
        protected Builder() {
            packet = new HttpResponse();
        }

        public Builder status(int status) {
            ((HttpResponse) packet).setStatus(status);
            return this;
        }

        public Builder reasonPhrase(String reasonPhrase) {
            ((HttpResponse) packet).setReasonPhrase(reasonPhrase);
            return this;
        }

        public final HttpResponse build() {
            return (HttpResponse) packet;
        }
    }
}
