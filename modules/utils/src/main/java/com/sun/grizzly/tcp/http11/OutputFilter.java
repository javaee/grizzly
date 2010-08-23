/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.tcp.http11;

import java.io.IOException;

import com.sun.grizzly.util.buf.ByteChunk;

import com.sun.grizzly.tcp.OutputBuffer;
import com.sun.grizzly.tcp.Response;

/**
 * Output filter.
 * 
 * @author Remy Maucherat
 */
public interface OutputFilter extends OutputBuffer {


    /**
     * Write some bytes.
     * 
     * @return number of bytes written by the filter
     */
    public int doWrite(ByteChunk chunk, Response unused)
        throws IOException;


    /**
     * Some filters need additional parameters from the response. All the 
     * necessary reading can occur in that method, as this method is called
     * after the response header processing is complete.
     */
    public void setResponse(Response response);


    /**
     * Make the filter ready to process the next request.
     */
    public void recycle();


    /**
     * Get the name of the encoding handled by this filter.
     */
    public ByteChunk getEncodingName();


    /**
     * Set the next buffer in the filter pipeline.
     */
    public void setBuffer(OutputBuffer buffer);


    /**
     * End the current request. It is acceptable to write extra bytes using
     * buffer.doWrite during the execution of this method.
     * 
     * @return Should return 0 unless the filter does some content length 
     * delimitation, in which case the number is the amount of extra bytes or
     * missing bytes, which would indicate an error. 
     * Note: It is recommended that extra bytes be swallowed by the filter.
     */
    public long end()
        throws IOException;


}
