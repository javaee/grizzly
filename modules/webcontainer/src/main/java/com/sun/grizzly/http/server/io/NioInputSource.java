/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.http.server.io;

import com.sun.grizzly.Buffer;


/**
 * <p>
 * This interface defines methods to allow an {@link java.io.InputStream} or
 * {@link java.io.Reader} to notify the developer <em>when</em> and <em>how much</em>
 * data is ready to be read without blocking.
 * </p>
 *
 * @since 2.0
 */
public interface NIOInputSource {


    /**
     * <p>
     * Notify the specified {@link ReadHandler} when any number of bytes
     * can be read without blocking.
     * </p>
     *
     * <p>
     * Invoking this method is equivalent to calling: notifyAvailable(handler, 0).
     * </p>
     *
     * @param handler than {@link ReadHandler} to notify.
     *
     * @see ReadHandler#onDataAvailable()
     * @see @see com.sun.grizzly.http.server.io.ReadHandler#onAllDataRead()
     */
    void notifyAvailable(final ReadHandler handler);


    /**
     * <p>
     * Notify the specified {@link ReadHandler} when the number of bytes that
     * can be read without blocking is greater or equal to the specified
     * <code>size</code>.
     * </p>
     *
     * @param handler the {@link ReadHandler} to notify.
     * @param size the least number of bytes that must be available before
     *  the {@link ReadHandler} is invoked.  If size is <code>0</code>, the
     *  handler will be notified as soon as data is available no matter the
     *  size.
     *
     * @see ReadHandler#onDataAvailable()
     * @see ReadHandler#onAllDataRead()
     */
    void notifyAvailable(final ReadHandler handler, final int size);


    /**
     * @return <code>true</code> when all data for this particular request
     *  has been read, otherwise returns <code>false</code>.
     */
    boolean isFinished();


    /**
     * @return the number of bytes (or characters) that may be obtained
     *  without blocking.
     */
    int readyData();


    /**
     * @return <code>true</code> if data can be obtained without blocking,
     *  otherwise returns <code>false</code>.
     */
    boolean isReady();


    /**
     * <p>
     * Returns the underlying {@link Buffer} that backs this
     *  <code>NIOInputSource</code>.
     * </p>
     *
     * <p>
     * It should be noted that for character-based <code>NIOInputSource</code>s,
     * the {@link Buffer} is the raw bytes.  Any required character conversion
     * would have to be applied manually.
     * </p>
     *
     * @return the underlying {@link Buffer} that backs this
     *  <code>NIOInputSource</code>.
     */
    Buffer getBuffer();

}
