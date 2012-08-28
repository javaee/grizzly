/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.asyncqueue;

/**
 * Context being passed when {@link org.glassfish.grizzly.Writer} refuses to
 * accept passed {@link WritableMessage} due to I/O or memory limitations.
 * User may perform one of the actions proposed by the context:
 * 1) {@link #cancel()} to cancel message writing
 * 2) {@link #retryWhenPossible()} to ask Grizzly to write the message once it's possible
 * 3) {@link #retryNow()} to ask Grizzly to try to write message again (not suggested)
 * 
 * @since 2.2
 * 
 * @deprecated push back logic is deprecated.
 * 
 * @author Alexey Stashok
 */
public abstract class PushBackContext {
    protected final AsyncWriteQueueRecord queueRecord;
    
    public PushBackContext(final AsyncWriteQueueRecord queueRecord) {
        this.queueRecord = queueRecord;
    }
   
    /**
     * The {@link PushBackHandler} passed along with one of the
     * {@link org.glassfish.grizzly.Writer}'s write(...) method call.
     * 
     * @return {@link PushBackHandler} passed along with write(...) call.
     */
    public PushBackHandler getPushBackHandler() {
        return queueRecord.getPushBackHandler();
    }
    
    /**
     * Returns the message size.
     * 
     * @return the message size.
     */
    public final long size() {
        return queueRecord.remaining();
    }
    
    /**
     * Instructs Grizzly to send this message once some resources get released.
     */
    public abstract void retryWhenPossible();
    
    /**
     * Instructs Grizzly to try to resend the message right now.
     */
    public abstract void retryNow();
    
    /**
     * Instructs Grizzly to cancel this message write and release message
     * associated resources.
     */
    public abstract void cancel();
    
    
}
