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


/**
 * Factory class used to create {@link ByteBuffer}. 
 *
 * The ByteBuffer can by direct (ByteBufferType.DIRECT) or heap (ByteBufferType.HEAP)
 * a view (ByteBufferType.DIRECT_VIEW) or ByteBufferType.HEAP_VIEW)
 * or backed by an array (ByteBufferType.HEAP_ARRAY).
 *
 * @author Jean-Francois Arcand
 */
public class ByteBufferFactory{

    /**
     * An enumeration of all type of ByteBuffer this object can create.
     */
    public enum ByteBufferType { DIRECT, HEAP, DIRECT_VIEW, HEAP_VIEW, HEAP_ARRAY }
    
    
    /**
     * The default capacity of the default view of a {@link ByteBuffer}
     */ 
    public final static int defaultCapacity = 8192;
    
    
    /**
     * The default capacity of the {@link ByteBuffer} from which views
     * will be created.
     */
    public static int capacity = 4000000; 
    
    
    /**
     * The {@link ByteBuffer} used to create direct byteBuffer view.
     */
    private static ByteBuffer directByteBuffer;
        
    
    /**
     * The {@link ByteBuffer} used to create direct byteBuffer view.
     */
    private static ByteBuffer heapByteBuffer;
    
    
    /**
     * Private constructor.
     */
    private ByteBufferFactory(){
    }
    
    
    /**
     * Return a direct {@link ByteBuffer} view
     * @param size the Size of the {@link ByteBuffer}
     * @param direct - direct or non-direct buffer?
     * @return {@link ByteBuffer}
     */ 
    public final static ByteBuffer allocateView(int size, boolean direct){
      return direct ? ByteBuffer.allocateDirect(size):ByteBuffer.allocate(size);
        /*if (direct && (directByteBuffer == null ||
               (directByteBuffer.capacity() - directByteBuffer.limit() < size))){
            directByteBuffer = ByteBuffer.allocateDirect(capacity);                 
        } else if (heapByteBuffer == null || 
               (heapByteBuffer.capacity() - heapByteBuffer.limit() < size)){
            heapByteBuffer = ByteBuffer.allocate(capacity);            
        }
        ByteBuffer byteBuffer = (direct ? directByteBuffer : heapByteBuffer);

        byteBuffer.limit(byteBuffer.position() + size);
        ByteBuffer view = byteBuffer.slice();
        byteBuffer.position(byteBuffer.limit());  
        
        return view;*/
    }

    
    /**
     * Return a direct {@link ByteBuffer} view using the default size.
     * @param direct - direct or non-direct buffer
     * @return {@link ByteBuffer}
     */ 
    public final static ByteBuffer allocateView(boolean direct){
        return direct ? ByteBuffer.allocateDirect(defaultCapacity) :
            ByteBuffer.allocate(defaultCapacity);
    }
     
    
    /**
     * Return a new ByteBuffer based on the requested <code>ByteBufferType</code>
     * @param type the requested <code>ByteBufferType</code>
     * @param size the {@link ByteBuffer} size.
     * @return a new ByteBuffer based on the requested <code>ByteBufferType</code>
     */
    public final static ByteBuffer allocate(ByteBufferType type,int size){
        return type != ByteBufferType.DIRECT ? ByteBuffer.allocate(size) :
            ByteBuffer.allocateDirect(size);
        /*if (type == ByteBufferType.HEAP){
            return ByteBuffer.allocate(size);
        } else if (type == ByteBufferType.HEAP_VIEW) {
            return allocateView(size,false);
        } else if (type == ByteBufferType.HEAP_ARRAY) {
            return ByteBuffer.wrap(new byte[size]);
        } else if (type == ByteBufferType.DIRECT){
           return ByteBuffer.allocateDirect(size); 
        } else if (type == ByteBufferType.DIRECT_VIEW){
            return allocateView(size,true);
        } else {
            throw new IllegalStateException("Invalid ByteBuffer Type");
        }*/
    }
}
