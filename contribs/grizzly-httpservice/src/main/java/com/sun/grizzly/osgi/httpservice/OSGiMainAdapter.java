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

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.*;

/**
 * OSGi Main Adapter.
 * <p/>
 * Dispatching adapter.
 *
 * @author Hubert Iwaniuk
 */
public class OSGiMainAdapter extends GrizzlyAdapter {
    private CleanMapper cm;
    private static Map<String, GrizzlyAdapter> registrations = new HashMap<String, GrizzlyAdapter>();

    public OSGiMainAdapter() {
        this.cm = new CleanMapper();
    }

    public void service(GrizzlyRequest request, GrizzlyResponse response) {
        boolean invoked = false;
        String alias = request.getDecodedRequestURI();
        while (true) {
            alias = cm.map(alias);
            if (alias == null) {
                break;
            } else {
                registrations.get(alias).service(request, response);
                invoked = true;
                if (response.getStatus() != 404) {
                    break;
                }
            }
        }
        if (!invoked) {
            // TODO: No registration matched, 404
        }
    }

    public void addGrizzlyAdapter(String alias, GrizzlyAdapter adapter) {
        if (!cm.contains(alias)) {
            // shold not happend, alias shouls be checked before.
            // TODO: signal it some how
        } else {
            cm.add(alias, adapter);
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

        public boolean add(String alias, GrizzlyAdapter adapter) {
            lock.writeLock().lock();
            try {
                boolean wasNew = aliasTree.add(alias);
                if (wasNew) {
                    registrations.put(alias, adapter);
                } else {
                    // TODO already registered, wtf
                }
                return wasNew;
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

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        final CleanMapper cm = new CleanMapper();
        cm.add("/a/b", null);
        cm.add("/a", null);
        cm.add("/a/b/c", null);

        final CountDownLatch latch = new CountDownLatch(1);

        Callable<Long> readingCallable = new Callable<Long>() {
            public Long call() throws Exception {
                latch.await();
                long start = System.currentTimeMillis();
                for (int i = 0; i < 100000; i++) {
                    String s = "/a/b/c/d" + i + "/" + i;
                    while (true) {
                        s = cm.map(s);
                        if (s == null) break;
                    }
                }
                return System.currentTimeMillis() - start;
            }
        };

        int READCOUNT = 8;
        int WRITECOUNT = 2;
        ExecutorService exec = Executors.newFixedThreadPool(READCOUNT + WRITECOUNT);
        List<Future<Long>> readFutures = new ArrayList<Future<Long>>(READCOUNT);
        List<Future<Long>> writeFutures = new ArrayList<Future<Long>>(WRITECOUNT);
        for (int i = 0; i < READCOUNT; i++) {
            readFutures.add(exec.submit(readingCallable));
        }
        for (int i = 0; i < WRITECOUNT; i++) {
            writeFutures.add(exec.submit(new MyCallableWriter(cm, i, latch)));
        }
        latch.countDown();
        for (int i = 0; i < READCOUNT; i++) {
            System.out.println("Read (" + i + ") finished in " + readFutures.get(i).get() + "ms.");
        }
        for (int i = 0; i < WRITECOUNT; i++) {
            System.out.println("Write (" + i + ") finished in " + writeFutures.get(i).get() + "ms.");
        }
        exec.shutdown();
    }

    private static class MyCallableWriter implements Callable<Long> {
        private final CleanMapper cm;
        private int j;
        private CountDownLatch latch;

        public MyCallableWriter(CleanMapper cm, int i, CountDownLatch latch) {
            this.cm = cm;
            j = i;
            this.latch = latch;
        }

        public Long call() throws Exception {
            latch.await();
            long start = System.currentTimeMillis();
            for (int i = 0; i < 100000; i++) {
                String s = "/" + j + "/" + i + "/a/b/c/d";
                cm.add(s, null);
            }
            return System.currentTimeMillis() - start;
        }
    }
}
