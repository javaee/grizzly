/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http2.frames;

import java.util.HashMap;
import java.util.Map;
import static org.glassfish.grizzly.http2.frames.PushPromiseFrame.FLAG_NAMES_MAP;

public abstract class HeaderBlockHead extends HeaderBlockFragment {

    public static final byte PADDED = 0x8;
    
    static final Map<Integer, String> FLAG_NAMES_MAP =
            new HashMap<Integer, String>(2);
    
    static {
        FLAG_NAMES_MAP.putAll(HeaderBlockFragment.FLAG_NAMES_MAP);
        FLAG_NAMES_MAP.put(Integer.valueOf(PADDED), "PADDED");
    }
    
    protected int padLength;
    
    // ---------------------------------------------------------- Public Methods

    public int getPadLength() {
        return padLength;
    }

    public boolean isPadded() {
        return isFlagSet(PADDED);
    }

    @Override
    protected Map<Integer, String> getFlagNamesMap() {
        return FLAG_NAMES_MAP;
    }
    
    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        if (DONT_RECYCLE) {
            return;
        }

        super.recycle();
    }


    // ---------------------------------------------------------- Nested Classes


    public static abstract class HeaderBlockHeadBuilder<T extends HeaderBlockHeadBuilder>
            extends HeaderBlockFragmentBuilder<T> {

        protected int padLength;
        
        // -------------------------------------------------------- Constructors


        protected HeaderBlockHeadBuilder() {
        }


        // ------------------------------------------------------ Public Methods


        public T padded(boolean isPadded) {
            if (isPadded) {
                setFlag(PushPromiseFrame.PADDED);
            }
            return getThis();
        }

        public T padLength(int padLength) {
            this.padLength = padLength;
            return getThis();
        }


    } // END SynStreamFrameBuilder

}
