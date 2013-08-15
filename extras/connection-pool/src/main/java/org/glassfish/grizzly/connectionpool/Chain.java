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
package org.glassfish.grizzly.connectionpool;

import java.util.LinkedList;

/**
 * Minimalistic linked list implementation.
 * This implementation doesn't work directly with objects, but their {@link Link}s,
 * so there is no performance penalty for locating object in the list.
 * 
 * The <tt>Chain</tt> implementation is not thread safe.
 * 
 * @author Alexey Stashok
 */
final class Chain<E> {
    /**
     * The size of the chain (number of elements stored).
     */
    private int size;
    
    /**
     * The first link in the chain
     */
    private Link<E> firstLink;
    /**
     * The last link in the chain
     */
    private Link<E> lastLink;
    
    /**
     * Returns <tt>true</tt> if this <tt>Chain</tt> doesn't have any element
     * stored, or <tt>false</tt> otherwise.
     */
    public boolean isEmpty() {
        return size == 0;
    }
    
    /**
     * Returns the number of elements stored in this <tt>Chain<tt>.
     */
    public int size() {
        return size;
    }

    /**
     * Returns the first {@link Link} in this <tt>Chain</tt>.
     */
    public Link<E> getFirstLink() {
        return firstLink;
    }

    /**
     * Returns the last {@link Link} in this <tt>Chain</tt>.
     */
    public Link<E> getLastLink() {
        return lastLink;
    }
    
    /**
     * Adds a {@link Link} to the beginning of this <tt>Chain</tt>.
     */
    public void offerFirst(final Link<E> link) {
        if (link.isAttached()) {
            throw new IllegalStateException("Already linked");
        }
        
        link.next = firstLink;
        if (firstLink != null) {
            firstLink.prev = link;
        }
        
        firstLink = link;
        if (lastLink == null) {
            lastLink = firstLink;
        }
        
        link.attach();
        
        size++;
    }
    
    /**
     * Adds a {@link Link} to the end of this <tt>Chain</tt>.
     */
    public void offerLast(final Link<E> link) {
        if (link.isAttached()) {
            throw new IllegalStateException("Already linked");
        }
        
        link.prev = lastLink;
        if (lastLink != null) {
            lastLink.next = link;
        }
        
        lastLink = link;
        if (firstLink == null) {
            firstLink = lastLink;
        }
        
        link.attach();
        
        size++;
    }
    
    /**
     * Removes and returns the last {@link Link} of this <tt>Chain<tt>.
     */
    public Link<E> pollLast() {
        if (lastLink == null) {
            return null;
        }
        
        final Link<E> link = lastLink;
        lastLink = link.prev;
        if (lastLink == null) {
            firstLink = null;
        } else {
            lastLink.next = null;
        }
        
        link.detach();
        
        size--;
        
        return link;
    }

    /**
     * Removes and returns the first {@link Link} of this <tt>Chain<tt>.
     */
    public Link<E> pollFirst() {
        if (firstLink == null) {
            return null;
        }
        
        final Link<E> link = firstLink;
        firstLink = link.next;
        if (firstLink == null) {
            lastLink = null;
        } else {
            firstLink.prev = null;
        }
        
        link.detach();
        
        size--;
        
        return link;
    }
    
    /**
     * Removes the {@link Link} from this <tt>Chain<tt>.
     * Unlike {@link LinkedList#remove(java.lang.Object)}, this operation is
     * cheap, because the {@link Link} already has information about its location
     * in the <tt>Chain<tt>, so no additional lookup needed.
     * 
     * @param link the {@link Link} to be removed.
     */
    public boolean remove(final Link<E> link) {
        if (!link.isAttached()) {
            return false;
        }
        
        final Link<E> prev = link.prev;
        final Link<E> next = link.next;
        if (prev != null) {
            prev.next = next;
        }
        
        if (next != null) {
            next.prev = prev;
        }

        link.detach();
        
        if (lastLink == link) {
            lastLink = prev;
            if (lastLink == null) {
                firstLink = null;
            }
        } else if (firstLink == link) {
            firstLink = next;
        }
        
        size--;
        
        return true;
    }

    /**
     * Moves the {@link Link} towards the <tt>Chain</tt>'s head by 1 element.
     * If the {@link Link} is already located at the <tt>Chain</tt>'s head -
     * the method invocation will not have any effect.
     * 
     * @param link the {@link Link} to be moved.
     */
    public void moveTowardsHead(final Link<E> link) {
        final Link<E> prev = link.prev;
        
        // check if this is head
        if (prev == null) {
            return;
        }
        
        final Link<E> next = link.next;
        final Link<E> prevPrev = prev.prev;
        
        if (prevPrev != null) {
            prevPrev.next = link;
        }
        
        link.prev = prevPrev;
        link.next = prev;
        
        prev.prev = link;
        prev.next = next;
        
        if (next != null) {
            next.prev = prev;
        }
    }
}
