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

package com.sun.grizzly.container;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.http.Parameters;
import java.io.IOException;

/**
 * This class represents an asynchronous request.
 *
 * @author Jeanfrancois Arcand
 */
public class GrizzletRequest {
    
    protected Request request;
    
    private GrizzletResponse response;
    
    private boolean requestParametersParsed = false;
    
    protected byte[] postData = null;
    
    private ByteChunk chunk = new ByteChunk();
    
    private byte[] body = new byte[8192];
    
    
    public GrizzletRequest(Request request) {
        this.request = request;
    }
    
    
    public Request getRequest() {
        return request;
    }
    
    
    protected void setRequest(Request request) {
        this.request = request;
    }
    
    
    public String[] getParameterValues(String s) {        
        Parameters parameters = request.getParameters();
        requestParametersParsed = true;
        
        parameters.setEncoding
                (com.sun.grizzly.tcp.Constants.DEFAULT_CHARACTER_ENCODING);
        parameters.setQueryStringEncoding
                (com.sun.grizzly.tcp.Constants.DEFAULT_CHARACTER_ENCODING);
        parameters.handleQueryParameters();
        int len = request.getContentLength();
        
        if (len > 0) {
            try {
                
                byte[] formData = getPostBody();
                if (formData != null) {
                    parameters.processParameters(formData, 0, len);
                }
            } catch (Throwable t) {
                ; // Ignore
            }
        }
        
        return parameters.getParameterValues(s);
    }
    
    
    protected byte[] getPostBody() throws IOException {
        int len = request.getContentLength();
        int actualLen = readPostBody(chunk, len);
        if (actualLen == len) {
            chunk.substract(body,0,chunk.getLength());
            chunk.recycle();
            return body;
        }
        return null;
    }
    
    
    /**
     * Read post body in an array.
     */
    protected int readPostBody(ByteChunk chunk,int len) throws IOException {
        
        int offset = 0;
        do {
            int inputLen = request.doRead(chunk);
            if (inputLen <= 0) {
                return offset;
            }
            offset += inputLen;
        } while ((len - offset) > 0);
        return len;
    }

    
    protected GrizzletResponse getResponse() {
        return response;
    }

    
    protected void setResponse(GrizzletResponse response) {
        this.response = response;
    }

}
