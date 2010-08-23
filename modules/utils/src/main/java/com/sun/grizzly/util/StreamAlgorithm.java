/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.util;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
/**
 * Generic parsing interface that can be used to implement protocol
 * specific logic parsing.
 *
 * @deprecated Use the ProtocolParser instead.
 * 
 * @author Jean-Francois Arcand
 */
public interface StreamAlgorithm<E>{
    
    
    /**
     * Return the stream content-length. If the content-length wasn't parsed,
     * return -1.
     * @return  content length or -1 if content length was not parsed
     */
    public int contentLength();
    
    
    /**
     * Return the stream header length. The header length is the length between
     * the start of the stream and the first occurance of character '\r\n' .
     * @return  header length
     */
    public int headerLength();
    
    
    /**
     * Allocate a {@link ByteBuffer}
     * @param useDirect allocate a direct {@link ByteBuffer}.
     * @param useView allocate a view {@link ByteBuffer}.
     * @param size the size of the newly created {@link ByteBuffer}.
     * @return a new {@link ByteBuffer}
     */
    public ByteBuffer allocate(boolean useDirect, boolean useView, int size);
    
    
    /**
     * Before parsing the bytes, initialize and prepare the algorithm.
     * @param byteBuffer the {@link ByteBuffer} used by this algorithm
     * @return {@link ByteBuffer} used by this algorithm
     */
    public ByteBuffer preParse(ByteBuffer byteBuffer);
    
    
    /**
     * Parse the {@link ByteBuffer} and try to determine if the bytes
     * stream has been fully read from the {@link SocketChannel}.
     * @param byteBuffer the bytes read.
     * @return true if the algorithm determines the end of the stream.
     */
    public boolean parse(ByteBuffer byteBuffer);
    
    
    /**
     * After parsing the bytes, post process the {@link ByteBuffer} 
     * @param byteBuffer the {@link ByteBuffer} used by this algorithm
     * @return {@link ByteBuffer} used by this algorithm
     */
    public ByteBuffer postParse(ByteBuffer byteBuffer);  
    
    
    /**
     * Recycle the algorithm.
     */
    public void recycle();
    
    
    /**
     * Rollback the {@link ByteBuffer} to its previous state in case
     * an error as occured.
     * @param byteBuffer
     * @return  {@link ByteBuffer}
     */
    public ByteBuffer rollbackParseState(ByteBuffer byteBuffer);  
    
    
    /**
     * The {@link Interceptor} associated with this algorithm.
     * @return {@link Interceptor}
     */
    public Interceptor getHandler();

    
    /**
     * Set the {@link Channel} used by this algorithm
     * @param channek set {@link Channel}
     */
    public void setChannel(E channel);
    
    
    /**
     * Set the <code>port</code> this algorithm is used.
     * @param port  port number
     */
    public void setPort(int port);
    
    
    /**
     * Return the port
     * @return  port number being used
     */
    public int getPort();

}

