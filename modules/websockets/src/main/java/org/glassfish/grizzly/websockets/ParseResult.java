/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.websockets;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.ThreadCache;

/**
 * {@link DataFrame} parse result.
 *
 * @author Alexey Stashok
 */
public class ParseResult implements Cacheable {
    // thread-local object cache
    private static final ThreadCache.CachedTypeIndex<ParseResult> CACHE_IDX =
        ThreadCache.obtainIndex(ParseResult.class, 1);
    // is complete
    private boolean isComplete;
    // remainder buffer (might not be null only if parsing was completed).
    private Buffer remainder;

    /**
     * Create a ParseResult object.
     *
     * @param isComplete was parsing completed?
     * @param remainderBuffer the remainder.
     *
     * @return <tt>ParseResult</tt>
     */
    public static ParseResult create(boolean isComplete, Buffer remainderBuffer) {
        ParseResult resultObject = ThreadCache.takeFromCache(CACHE_IDX);
        if (resultObject == null) {
            resultObject = new ParseResult();
        }
        resultObject.isComplete = isComplete;
        resultObject.remainder = remainderBuffer;
        return resultObject;
    }

    private ParseResult() {
    }

    /**
     * Get the parsing remainder {@link Buffer}. May not be null only in case, when parsing was completed, but some data
     * is still ready for parsing.
     *
     * @return the parsing remainder {@link Buffer}. May not be null only in case, when parsing was completed, but some
     *         data is still ready for parsing.
     */
    public Buffer getRemainder() {
        return remainder;
    }

    /**
     * Returns <tt>true</tt>, if parsing was completed, or <tt>false</tt> if more data is expected.
     *
     * @return <tt>true</tt>, if parsing was completed, or <tt>false</tt> if more data is expected.
     */
    public boolean isComplete() {
        return isComplete;
    }

    /**
     * Recycle the object.
     */
    @Override
    public void recycle() {
        remainder = null;
        isComplete = false;
        ThreadCache.putToCache(CACHE_IDX, this);
    }
}