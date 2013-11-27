/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.filterchain;

import org.glassfish.grizzly.Appender;
import org.glassfish.grizzly.Event;

/**
 * The state holder, responsible for keeping a {@link org.glassfish.grizzly.Connection}
 * state for a given {@link Event} associated with a {@link Filter}-in-{@link FilterChain}.
 */
public class FilterState {
    private boolean isUnparsed;
    private Appender appender;
    private Object remainder;
    
    private final Event event;

    protected FilterState(final Event event) {
        this.event = event;
    }

    /**
     * @return the {@link Event} the state is associated with
     */
    protected Event getEvent() {
        return event;
    }
        
    /**
     * @return <tt>true</tt> if the state represents unparsed message, which
     *         can contain another ready message, or <tt>false</tt> for incomplete
     *         message
     */
    public boolean isUnparsed() {
        return isUnparsed;
    }

    protected void setUnparsed(boolean isUnparsed) {
        this.isUnparsed = isUnparsed;
    }
    
    /**
     * @return {@link Appender}, which will be used to append incomplete messages
     *         before passing them to a {@link Filter}
     */
    public Appender getAppender() {
        return appender;
    }

    protected void setAppender(Appender appender) {
        this.appender = appender;
    }
    
    /**
     * @return actual state message, which may represent either incomplete or
     *         unparsed message
     */
    public Object getRemainder() {
        return remainder;
    }
 
    /**
     * @return <tt>true</tt> if there is some message (state) associated
     */
    public boolean isReady() {
        return remainder != null;
    }
    
    /**
     * Resets the <tt>FilterState</tt> and makes it ready to be reused.
     */
    public void reset() {
        remainder = appender = null;
    }
    
    protected void setRemainder(Object remainder) {
        this.remainder = remainder;
    }    
}
