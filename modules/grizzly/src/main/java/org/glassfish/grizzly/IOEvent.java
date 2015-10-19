/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import java.nio.channels.SelectionKey;

/**
 * Enumeration represents the I/O events, occurred on a {@link Connection}.
 *
 * @see Connection
 * 
 * @author Alexey Stashok
 */
public enum IOEvent {

    /**
     * no event
     */
    NONE(0),

    /**
     * Event occurs on a {@link Connection}, once it gets available for read.
     */
    READ(SelectionKey.OP_READ),

    /**
     * Event occurs on a {@link Connection}, once it  gets available for write.
     */
    WRITE(SelectionKey.OP_WRITE),

    /**
     * Event occurs on a server {@link Connection}, when it becomes ready
     * to accept new client {@link Connection}.
     *
     * Note, this event occurs on server code for server {@link Connection}.
     */
    SERVER_ACCEPT(SelectionKey.OP_ACCEPT),

    /**
     * Event occurs on a client {@link Connection}, just after it was accepted
     * by the server.
     *
     * Note, this event occurs on server code for client {@link Connection}.
     */
    ACCEPTED(0),

    /**
     * Event occurs on a {@link Connection}, once it was connected to server.
     * 
     * (this is service IOEvent, which is not getting propagated to a {@link Processor}
     */
    CLIENT_CONNECTED(SelectionKey.OP_CONNECT),

    /**
     * Event occurs on a {@link Connection}, once it was connected to server.
     */
    CONNECTED(0),
    
    /**
     * Event occurs on a {@link Connection}, once it gets closed.
     */
    CLOSED(0);
    
    private final int selectionKeyInterest;

    IOEvent(int selectionKeyInterest) {
        this.selectionKeyInterest = selectionKeyInterest;
    }
    
    public int getSelectionKeyInterest() {
        return selectionKeyInterest;
    }
}
