/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.utils;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import org.glassfish.grizzly.Grizzly;

/*
 * Utility class, which tries to pickup the best collection implementation depending
 * on running environment.
 * 
 * @author gustav trede
 */
public class DataStructures {

    private final static Class<?> LTQclass;

    static {
        Class<?> c = null;
        try {
            JdkVersion jdkVersion = JdkVersion.getJdkVersion();
            JdkVersion minimumVersion = JdkVersion.parseVersion("1.7.0");
            c = getAndVerify((minimumVersion.compareTo(jdkVersion) <= 0)
                    ? "java.util.concurrent.LinkedTransferQueue"
                    : "org.glassfish.grizzly.utils.LinkedTransferQueue");
            Grizzly.logger(DataStructures.class).log(Level.FINE, "USING LTQ class:{0}", c);
        } catch (Throwable t) {
            Grizzly.logger(DataStructures.class).log(Level.FINE,
                    "failed loading datastructure class:" + c, t);
        }
        LTQclass = c;
    }

    private static Class<?> getAndVerify(String cname) throws Throwable {
        return DataStructures.class.getClassLoader().loadClass(cname).newInstance().getClass();
    }

    @SuppressWarnings("unchecked")
    public static <T> BlockingQueue<T> getLTQInstance() {
        try {
            return (BlockingQueue<T>) LTQclass.newInstance();
        } catch (Exception ea) {
            throw new RuntimeException(ea);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> BlockingQueue<T> getLTQInstance(Class<T> t) {
        try {
            return (BlockingQueue<T>) LTQclass.newInstance();
        } catch (Exception ea) {
            throw new RuntimeException(ea);
        }
    }
}
