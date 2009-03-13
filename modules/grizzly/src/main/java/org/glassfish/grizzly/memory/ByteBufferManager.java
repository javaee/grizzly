/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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

package org.glassfish.grizzly.memory;

import java.nio.ByteBuffer;

/**
 * The simple Buffer manager implementation, 
 *
 * @author Jean-Francois Arcand
 * @author Alexey Stashok
 */
public class ByteBufferManager implements MemoryManager<ByteBufferWrapper> {
    protected boolean isDirect;
    
    public ByteBufferManager() {
        this(false);
    }

    public ByteBufferManager(boolean isDirect) {
        this.isDirect = isDirect;
    }

    protected ByteBuffer allocate0(int size) {
        if (isDirect) {
            return ByteBuffer.allocateDirect(size);
        } else {
            return ByteBuffer.allocate(size);
        }
    }

    public ByteBufferWrapper allocate(final int size) {
        return wrap(allocate0(size));
    }

    public ByteBufferWrapper reallocate(ByteBufferWrapper oldBuffer,
            int newSize) {
        ByteBufferWrapper newBuffer = allocate(newSize);
        oldBuffer.flip();
        newBuffer.put(oldBuffer);
        return newBuffer;
    }

    public void release(ByteBufferWrapper buffer) {
    }

    public boolean isDirect() {
        return isDirect;
    }

    public void setDirect(boolean isDirect) {
        this.isDirect = isDirect;
    }

    protected ByteBufferWrapper wrap(ByteBuffer byteBuffer) {
        return new ByteBufferWrapper(this, byteBuffer);
    }
}
