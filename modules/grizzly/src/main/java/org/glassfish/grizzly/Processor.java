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

package org.glassfish.grizzly;

import java.io.IOException;

/**
 * Processor implementations are responsible for processing events, 
 * occured on connection.
 * 
 * @author Alexey Stashok
 */
public interface Processor<E extends Context> {
    /**
     * Creates {@link Context}
     * 
     * @return {@link Context}, or <tt>null</tt>, if default {@link Context}
     *         could be used.
     */
    public E context();

    /**
     * Method will be called by framework before passing this {@link Processor}
     * to worker thread.
     *
     * @param context processing context
     * @throws java.io.IOException
     */
    public void beforeProcess(E context) throws IOException;

    /**
     * Method will be called by framework to process some event, which
     * occured on a connection
     * 
     * @param context processing context
     * @return the result of I/O event processing
     * @throws java.io.IOException
     */
    public ProcessorResult process(E context) throws IOException;

    /**
     * Method will be called by framework after {@link Processor} execution,
     * so it can release associated resources.
     *
     * @param context processing context
     * @throws java.io.IOException
     */
    public void afterProcess(E context) throws IOException;

    /**
     * Is this {@link Processor} interested in processing the i/o event
     * 
     * @param ioEvent
     * @return true, if this {@link Processor} is interested and execution
     * process will start, false otherwise.
     */
    public boolean isInterested(IOEvent ioEvent);

    /**
     * Set the the i/o event, this {@link Processor} is interested in
     * 
     * @param ioEvent {@link IOEvent}
     * @param isInterested true, if {@link Processor} is interested in
     *                     processing of the I/O event, or false otherwise.
     */
    public void setInterested(IOEvent ioEvent, boolean isInterested);
}
