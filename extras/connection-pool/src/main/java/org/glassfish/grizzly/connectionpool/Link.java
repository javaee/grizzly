/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.connectionpool;

/**
 * The object represents an element, from which a minimalistic {@link Chain} is built.
 * 
 * It is possible to attach or detach a <tt>Link</tt> from a {@link Chain}. When
 * the <tt>Link</tt> is attached it contains a pointers to the previous and
 * the next <tt>Link</tt>s in the {@link Chain}, otherwise, if the <tt>Link</tt>
 * is detached - the pointers values are <tt>null</tt>.
 * 
 * If a <tt>Link</tt> is attached - it can only be attached to one {@link Chain}.
 * 
 * @param <E>
 * @author Alexey Stashok
 */
public final class Link<E> {
    /**
     * The Link payload/value
     */
    private final E value;
    
    /**
     * The pointer to the previous link in the chain
     */
    Link<E> prev;
    /**
     * The pointer to the next link in the chain
     */
    Link<E> next;
    
    /**
     * attachment flag
     */
    private boolean isAttached;
    /**
     * The attachment timestamp, which shows the time when the link was attached.
     */
    private long linkTimeStamp = -1;
    

    /**
     * Construct the <tt>Link</tt> holding given value object.
     * @param value an object the <tt>Link</tt> represents.
     */
    public Link(final E value) {
        this.value = value;
    }

    /**
     * @return the value held by this {@link Link}.
     */
    public E getValue() {
        return value;
    }
    
    /**
     * Attaches the <tt>Link</tt> to a {@link Chain}.
     */
    void attach() {
        linkTimeStamp = System.currentTimeMillis();
        isAttached = true;
    }
    
    /**
     * Detaches the <tt>Link</tt> from a {@link Chain}.
     */
    void detach() {
        isAttached = false;
        linkTimeStamp = -1;
        prev = next = null;
    }

    /**
     * @return the timestamp, that represents the time (in milliseconds) when
     * the <tt>Link</tt> was attached to a {@link Chain}, or <tt>-1</tt> if the
     * <tt>Link</tt> is not currently attached to a {@link Chain}.
     */
    public long getAttachmentTimeStamp() {
        return linkTimeStamp;
    }
    
    /**
     * @return <tt>true</tt> if the <tt>Link</tt> is currently attached to a
     * {@link Chain} or <tt>false</tt> otherwise.
     */
    public boolean isAttached() {
        return isAttached;
    }

    @Override
    public String toString() {
        return "Link{"
                + "value=" + value
                + ", prev=" + prev
                + ", next=" + next
                + ", isAttached=" + isAttached
                + ", linkTimeStamp=" + linkTimeStamp
                + "} " + super.toString();
    }
}
