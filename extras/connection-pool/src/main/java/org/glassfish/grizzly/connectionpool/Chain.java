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

/**
 *
 * @author oleksiys
 */
final class Chain<E> {
    private int size;
    
    private Link<E> firstLink;
    private Link<E> lastLink;
    
    public boolean isEmpty() {
        return size == 0;
    }
    
    public int size() {
        return size;
    }

    public Link<E> getFirstLink() {
        return firstLink;
    }

    public Link<E> getLastLink() {
        return lastLink;
    }
    
    public void offer(final Link<E> link) {
        if (link.isLinked()) {
            throw new IllegalStateException("Already linked");
        }
        
        link.link(lastLink, null);
        lastLink = link;
        
        if (firstLink == null) {
            firstLink = lastLink;
        }
        
        size++;
    }
    
    public Link<E> pollLast() {
        if (lastLink == null) {
            return null;
        }
        
        final Link<E> link = lastLink;
        lastLink = link.prev;
        if (lastLink == null) {
            firstLink = null;
        }
        
        link.unlink();
        
        size--;
        
        return link;
    }

    public Link<E> pollFirst() {
        if (firstLink == null) {
            return null;
        }
        
        final Link<E> link = firstLink;
        firstLink = link.next;
        if (firstLink == null) {
            lastLink = null;
        }
        
        link.unlink();
        
        size--;
        
        return link;
    }
    
    public boolean remove(final Link<E> link) {
        if (!link.isLinked()) {
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

        link.unlink();
        
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

    public void moveTowardsHead(final Link<E> link) {
        final Link<E> prev = link.prev;
        
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
