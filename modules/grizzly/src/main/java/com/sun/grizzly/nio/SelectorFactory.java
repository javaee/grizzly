/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.nio;

import java.io.IOException;
import java.nio.channels.Selector;

/**
 *
 * @author oleksiys
 */
public abstract class SelectorFactory {
    private static volatile SelectorFactory instance;
    
    public static SelectorFactory instance() {
        if (instance == null) {
            synchronized(SelectorFactory.class) {
                if (instance == null) {
                    instance = new DefaultSelectorFactory();
                }
            }
        }
        return instance;
    }

    public static void setInstance(SelectorFactory instance) {
        SelectorFactory.instance = instance;
    }
    
    public abstract Selector create() throws IOException;
    
    public static class DefaultSelectorFactory extends SelectorFactory {
        @Override
        public Selector create() throws IOException {
            try {
                return Selector.open();
            } catch (NullPointerException e) {
                // This is a JDK issue http://bugs.sun.com/view_bug.do?bug_id=6427854
                // Try 5 times and abort
                for (int i = 0; i < 5; i++) {
                    try {
                        return Selector.open();
                    } catch (NullPointerException e2) {
                    }
                }

                String msg = e.getMessage();
                throw new IOException("Can not open Selector" + (msg == null ? "" : msg));
            }
        }
    }
}
