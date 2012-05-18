/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.ajp;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.buf.MessageBytes;
import java.util.logging.Logger;

/**
 * {@link Request} implementation, which also contains AJP
 * related meta data.
 *
 * @author Alexey Stashok
 */
public final class AjpHttpRequest extends Request {
    private static final Logger LOGGER = Logger.getLogger(AjpHttpRequest.class.getName());
    private boolean expectContent;

    final MessageBytes tmpMessageBytes = MessageBytes.newInstance();
    
    public static AjpHttpRequest create() {
        return new AjpHttpRequest();
    }

    private final MessageBytes sslCert = MessageBytes.newInstance();

    private String secret;
    
    private int length = -1;
    private int type = -1;    

    private final AjpHttpResponse response = new AjpHttpResponse();

    private boolean isForwardRequestProcessing;
    private int contentBytesRemaining = -1;

    public AjpHttpRequest() {
        response.setRequest(this);
        setResponse(response);
    }

    public MessageBytes sslCert() {
        return sslCert;
    }

    public String getSecret() {
        return secret;
    }

    void setSecret(final String secret) {
        this.secret = secret;
    }

    public int getLength() {
        return length;
    }

    protected void setLength(int length) {
        this.length = length;
    }

    public int getType() {
        return type;
    }

    protected void setType(int type) {
        this.type = type;
    }
    
    public int getContentBytesRemaining() {
        return contentBytesRemaining;
    }

    public void setContentBytesRemaining(final int contentBytesRemaining) {
        this.contentBytesRemaining = contentBytesRemaining;
    }

    @Override
    public void recycle() {
        isForwardRequestProcessing = false;
        tmpMessageBytes.recycle();
        contentBytesRemaining = -1;
        response.recycle();
        sslCert.recycle();
        secret = null;
        length = -1;
        type = -1;
        
        super.recycle();
    }
    
    public boolean isExpectContent() {
        return expectContent;
    }

    public void setExpectContent(boolean expectContent) {
        this.expectContent = expectContent;
    }

    public boolean isForwardRequestProcessing() {
        return isForwardRequestProcessing;
    }

    public void setForwardRequestProcessing(boolean isForwardRequestProcessing) {
        this.isForwardRequestProcessing = isForwardRequestProcessing;
    }
}