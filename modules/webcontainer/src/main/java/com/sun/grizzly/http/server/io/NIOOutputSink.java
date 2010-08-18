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


/**
 * <p>
 * This interface defines methods to allow an {@link java.io.OutputStream} or
 * {@link java.io.Writer} to allow the developer to check with the runtime
 * whether or not it's possible to write a certain amount of data, or if it's
 * not possible, to be notified when it is.
 * </p>
 *
 * @since 2.0
 */
public interface NIOOutputSink {


    /**
     * Instructs the <code>NIOOutputSink</code> to invoke the provided
     * {@link WriteHandler} when it is possible to write <code>length</code>
     * bytes.
     *
     * Note that once the {@link WriteHandler} has been notified, it will not
     * be considered for notification again at a later point in time. 
     *
     * @param handler the {@link WriteHandler} that should be notified
     *  when it's possible to write <code>length</code> bytes.
     * @param length the number of bytes that require writing.
     */
    void notifyCanWrite(final WriteHandler handler, final int length);


    /**
     * @param length specifies the number of bytes that require writing
     *
     * @return <code>true</code> if a write to this <code>NIOOutputSink</code>
     *  will succeed, otherwise returns <code>false</code>.
     */
    boolean canWrite(final int length);

}
