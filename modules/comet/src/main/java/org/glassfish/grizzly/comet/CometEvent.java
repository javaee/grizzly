/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.comet;

/**
 * Simple event class used to pass information between {@link CometHandler} and the Comet implementation.
 *
 * @author Jeanfrancois Arcand
 */
public class CometEvent<E> {
    public enum Type {
        INTERRUPT,
        NOTIFY,
        INITIALIZE,
        TERMINATE,
        READ,
        WRITE,
    }

    /**
     * This type of event.
     */
    protected Type type;
    /**
     * Share an <code>E</code> amongst {@link CometHandler}
     */
    protected E attachment;
    /**
     * The CometContext from where this instance was fired.
     */
    private CometContext cometContext;
    private static final long serialVersionUID = 920798330036889926L;

    /**
     * Create a new <code>CometEvent</code>
     */
    public CometEvent() {
        type = Type.NOTIFY;
    }

    public CometEvent(Type type) {
        this.type = type;
    }

    public CometEvent(Type type, CometContext context) {
        this.type = type;
        cometContext = context;
    }

    public CometEvent(Type type, CometContext cometContext, E attachment) {
        this.type = type;
        this.attachment = attachment;
        this.cometContext = cometContext;
    }

    /**
     * Return the <code>type</code> of this object.
     *
     * @return int Return the <code>type</code> of this object
     */
    public Type getType() {
        return type;
    }

    /**
     * Set the <code>type</code> of this object.
     *
     * @param type the <code>type</code> of this object
     */
    protected void setType(Type type) {
        this.type = type;
    }

    /**
     * Attach an <E>
     *
     * @param attachment An attachment.
     */
    public void attach(E attachment) {
        this.attachment = attachment;
    }

    /**
     * Return the attachment <E>
     *
     * @return attachment An attachment.
     */
    public E attachment() {
        return attachment;
    }

    /**
     * Return the {@link CometContext} that fired this event.
     */
    public CometContext getCometContext() {
        return cometContext;
    }

    /**
     * Set the {@link CometContext} that fired this event.
     */
    protected void setCometContext(CometContext cometContext) {
        this.cometContext = cometContext;
    }
}
