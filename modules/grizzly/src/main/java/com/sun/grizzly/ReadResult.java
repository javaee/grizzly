/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
 * Result of read operation, retuned by {@link Readable}.
 * 
 * @author Alexey Stashok
 */
public class ReadResult<K, L> implements Result {
    /**
     * Connection, from which data were read.
     */
    private final Connection connection;

    /**
     * message data
     */
    private K message;

    /**
     *  Source address.
     */

    private L srcAddress;

    /**
     * Number of bytes read.
     */
    private int readSize;

    public ReadResult(Connection connection) {
        this(connection, null, null, 0);
    }

    public ReadResult(Connection connection, K message, L srcAddress,
            int readSize) {
        this.connection = connection;
        this.message = message;
        this.srcAddress = srcAddress;
        this.readSize = readSize;
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
     * Get the source address, the message was read from.
     *
     * @return the source address, the message was read from.
     */
    public final L getSrcAddress() {
        return srcAddress;
    }

    /**
     * Set the source address, the message was read from.
     *
     * @param srcAddress the source address, the message was read from.
     */
    public final void setSrcAddress(L srcAddress) {
        this.srcAddress = srcAddress;
    }

    /**
     * Get the number of bytes, which were read.
     *
     * @return the number of bytes, which were read.
     */
    public final int getReadSize() {
        return readSize;
    }

    /**
     * Set the number of bytes, which were read.
     *
     * @param readSize the number of bytes, which were read.
     */
    public final void setReadSize(int readSize) {
        this.readSize = readSize;
    }
}
