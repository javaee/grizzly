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
package com.sun.grizzly.osgi.httpservice;

import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;

import java.util.TreeSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * OSGi Main Adapter.
 * <p/>
 * Dispatching adapter.
 *
 * @author Hubert Iwaniuk
 */
public class OSGiMainAdapter extends GrizzlyAdapter {

    public void service(GrizzlyRequest request, GrizzlyResponse response) {
        // TODO implement
    }

    public static void main(String[] args) {
        CleanMapper cm = new CleanMapper();
        cm.add("/a/b");
        cm.add("/a");
        cm.add("/a/b/c");

        long start;
        for (int j = 0; j < 10; j++) {
            start = System.currentTimeMillis();
            for (int i = 0; i < 100000; i++) {
                String s = "/a/b/c/d" + j + "/" + i;
                while (true) {
                    s = cm.map(s);
                    if (s == null) break;
                    //                System.out.println(s);
                }
            }
            System.out.println("Took " + (System.currentTimeMillis() - start) + "ms.");
        }
    }

    static class CleanMapper {
        private static final ReadWriteLock lock = new ReentrantReadWriteLock();
        private TreeSet<String> aliasTree = new TreeSet<String>();

        public String map(String resource) {
            String match = resource;
            while (true) {
                int i = match.lastIndexOf('/');
                if (i == -1) {
                    return null;
                } else {
                    if (i == 0)
                        match = "/";
                    else
                        match = resource.substring(0, i);
                }
                if (contains(match)) {
                    return match;
                } else if (i == 0) {
                    return null;
                }
            }
        }

        public boolean add(String s) {
            lock.writeLock().lock();
            try {
                return aliasTree.add(s);
            } finally {
                lock.writeLock().unlock();
            }
        }

        public boolean remove(String s) {
            lock.writeLock().lock();
            try {
                return aliasTree.remove(s);
            } finally {
                lock.writeLock().unlock();
            }
        }

        public boolean contains(String s) {
            lock.readLock().lock();
            try {
                return aliasTree.contains(s);
            } finally {
                lock.readLock().unlock();
            }
        }
    }
}
