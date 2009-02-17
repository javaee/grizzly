/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
package org.glassfish.grizzly.bbs ;

import org.glassfish.grizzly.bbs.BufferWrapper;
import java.io.Closeable ;


/** Interface describing an allocator of BufferWrappers.  This is used to
 * hide the complexities of direct vs. indirect ByteBuffer management.
 * Allocator.BufferWrapper is used by the Reader and Writer interfaces.
 * An Allocator is Closeable, and all methods on the Allocator will throw
 * IllegalStateException after a close() call.
 */
public interface Allocator extends Closeable {
    public enum BufferType { DIRECT, HEAP } ;

    /** Return the maximum size that may be prepended to a BufferWrapper allocated
     * by this allocator without extra copying.
     */
    int headerSize() ;

    /** Returns the maximum size that can be allocated by an allocate( int ) call.
     */
    int maxAllocationSize() ;

    /** Return the underlying buffer type used in this allocator.
     */
    BufferType bufferType() ;

    /** Allocate a buffer of size bytes.  Returns null if allocation is not
     * possible. This buffer starts with position set to 0, limit set to 
     * capacity, and mark undefined.
     */
    BufferWrapper allocate( int size ) ;

    /** Allocate a buffer with the largest possible size between minSize and maxSize.
     * Returns null if allocation is not possible.  This may typically be used for allocating
     * the largest possible buffer for a channel read operation.  Once the read completes,
     * trim() may be called on the buffer to reclaim any unused space.  Note that the trim
     * operation typically only succeeds if there are no intervening allocate calls between the
     * allocate of a buffer and the trim call.
     */
    BufferWrapper allocate( int minSize, int maxSize ) ;
}

