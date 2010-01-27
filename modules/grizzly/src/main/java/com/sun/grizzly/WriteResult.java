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

package com.sun.grizzly;

/**
 * Result of write operation, retuned by {@link Writable}.
 *
 * @author Alexey Stashok
 */
public class WriteResult<K, L> implements Result {
    /**
     * Connection, from which data were read.
     */
    private final Connection connection;

    /**
     * message data
     */
    private K message;

    /**
     *  Destination address.
     */

    private L dstAddress;

    /**
     * Number of bytes written.
     */
    private int writtenSize;

    public WriteResult(Connection connection) {
        this(connection, null, null, 0);
    }

    public WriteResult(Connection connection, K message, L dstAddress,
            int writeSize) {
        this.connection = connection;
        this.message = message;
        this.dstAddress = dstAddress;
        this.writtenSize = writeSize;
    }

    /**
     * Get the {@link Connection} data were read from.
     *
     * @return the {@link Connection} data were read from.
     */
    @Override
    public final Connection getConnection() {
        return connection;
    }

    /**
     * Get the message, which was read.
     *
     * @return the message, which was read.
     */
    public final K getMessage() {
        return message;
    }

    /**
     * Set the message, which was read.
     *
     * @param message the message, which was read.
     */
    public final void setMessage(K message) {
        this.message = message;
    }

    /**
     * Get the destination address, the message was written to.
     *
     * @return the destination address, the message was written to.
     */
    public final L getDstAddress() {
        return dstAddress;
    }

    /**
     * Set the destination address, the message was written to.
     *
     * @param dstAddress the destination address, the message was written to.
     */
    public final void setDstAddress(L dstAddress) {
        this.dstAddress = dstAddress;
    }

    /**
     * Get the number of bytes, which were written.
     *
     * @return the number of bytes, which were written.
     */
    public final int getWrittenSize() {
        return writtenSize;
    }

    /**
     * Set the number of bytes, which were written.
     *
     * @param writeSize the number of bytes, which were written.
     */
    public final void setWrittenSize(int writeSize) {
        this.writtenSize = writeSize;
    }
}
