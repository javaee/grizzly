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
package com.sun.grizzly.memory.slab;

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.Grizzly;

/**
 * @author Ken Cavanaugh
 * @author John Vieten
 */
public class Slab {

    private static Logger logger = Grizzly.logger(Slab.class);
    
    private static boolean FINE_DEBUG;
    static {
        FINE_DEBUG = logger.isLoggable(Level.FINEST);
    }


    private void msg(final String msg) {
        logger.log(Level.FINEST, msg);
    }

    // A Slab is used only by an Allocator, so questions of concurrent 
    // access should be handled at the Allocator level.
    public enum State {

        EMPTY, PARTIAL, FULL
    };
    private ByteBuffer space;
    private int _sizeDisposed;

    // Only for use in BufferWrapper.
    // Note that the implementation is exactly the same as
    // sizeAllocated.  However, the semantics are different:
    // currentPosition is the allocation pointer at the time of
    // an allocate call, which sizeAllocated is the total space
    // allocated.  If the implementation changes, we still want
    // to preserve the semantics.
    public int currentPosition() {
        return space.position();
    }

    public State getState() {
        if (sizeAllocated() == 0) {
            return State.EMPTY;
        } else if (sizeAvailable() == 0) {
            return State.FULL;
        } else {
            return State.PARTIAL;
        }
    }

    public int totalSize() {
        return space.capacity();
    }

    public int sizeAvailable() {
        return space.remaining();
    }

    public int sizeAllocated() {
        return space.position();
    }

    public int sizeDisposed() {
        return _sizeDisposed;
    }

    public void markFull() {
        final ByteBuffer result = allocate(sizeAvailable());
        dispose(result);
    }

    public void markEmpty() {
        space.position(0);
    }

    public Slab(final int size, final boolean bufferType) {
        if (FINE_DEBUG) {
            msg("Constructor: allocating " + size + " for BufferType " + bufferType);
        }

        try {
            if (bufferType) {
                space = ByteBuffer.allocateDirect(size);
            } else {
                space = ByteBuffer.allocate(size);
            }

            initialize();
        } catch (Error err) {
            if (FINE_DEBUG) {
                msg("Error " + err + " in constructor: size = " + size + " BufferType = " + bufferType);
            }

            throw err;
        } catch (RuntimeException exc) {
            if (FINE_DEBUG) {
                msg("RuntimeException " + exc + " in constructor: size = " + size + " BufferType = " + bufferType );
            }

            throw exc;
        }
    }

    private void initialize() {
        space.limit(space.capacity());
        space.position(0);
        _sizeDisposed = 0;
    }

    public ByteBuffer allocate(final int size) {
        ByteBuffer result = null;
        if (size <= space.remaining()) {
            space.limit(space.position() + size);
            result = space.slice();
            space.position(space.limit());
            space.limit(space.capacity());
        }

        return result;
    }

    public void dispose(final ByteBuffer buffer) {
        if (getState() == State.EMPTY) {
            throw new IllegalStateException(
                    "Attempt to disposed of a buffer to an empty Slab!");
        }

        _sizeDisposed += buffer.capacity();
        if (getState() == State.FULL && _sizeDisposed >= sizeAllocated()) {
            initialize();
        }
    }

    public ByteBuffer trim(final int bufferStartPosition,
            final ByteBuffer buffer, final int sizeNeeded) {
        ByteBuffer result = buffer;

        // Check to see if buffer is the last buffer allocated
        if ((bufferStartPosition + buffer.capacity()) == space.position()) {
            space.position(bufferStartPosition);
            result = allocate(sizeNeeded);
        }

        return result;
    }
}
