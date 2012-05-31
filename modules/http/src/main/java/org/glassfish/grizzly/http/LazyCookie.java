/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.ThreadCache;

/**
 * Lazy wrapper over {@link Cookie}.
 * The String representations of the cookie's attributes will be initialized on the first get...() call.
 *
 * @author Alexey Stashok
 */
public class LazyCookie extends Cookie {

    private static final ThreadCache.CachedTypeIndex<LazyCookie> CACHE_IDX =
                ThreadCache.obtainIndex(LazyCookie.class, 16);

    private final LazyCookieState lazyState = new LazyCookieState();

    private boolean isInitialized;


    // ------------------------------------------------------------ Constructors


    private LazyCookie() {
        putToCache = false;
    }


    // ---------------------------------------------------------- Public Methods


    public static LazyCookie create() {
        LazyCookie cookie = ThreadCache.takeFromCache(CACHE_IDX);
        if (cookie == null) {
            cookie = new LazyCookie();
        }
        return cookie;
    }


    // ----------------------------------------------------- Methods from Cookie


    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        checkInitialized();
        return super.getName();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getValue() {
        checkInitialized();
        return super.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        checkInitialized();
        return super.getVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getComment() {
        checkInitialized();
        return super.getComment();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDomain() {
        checkInitialized();
        return super.getDomain();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxAge() {
        checkInitialized();
        return super.getMaxAge();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPath() {
        checkInitialized();
        return super.getPath();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSecure() {
        checkInitialized();
        return super.isSecure();
    }


    
    /**
     * Returns the lazy state representation.
     * @return the lazy state representation.
     */
    public LazyCookieState lazy() {
        return lazyState;
    }


    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        super.recycle();
        isInitialized = false;
        lazyState.recycle();
        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // ------------------------------------------------------- Protected Methods


    @Override
    protected boolean lazyNameEquals(String name) {
        return lazyState.getName().equals(name);
    }


    protected final void checkInitialized() {
        if (!isInitialized) {
            isInitialized = true;
            initialize();
        }
    }

    protected void initialize() {
        final String strName = lazyState.getName().toString();
        checkName(strName);

        name = strName;

        value = unescape(lazyState.getValue().toString());
        path = unescape(lazyState.getPath().toString());

        final String domainStr = lazyState.getDomain().toString();
        if (domainStr != null) {
            domain = unescape(domainStr); //avoid NPE
        }

        final String commentStr = lazyState.getComment().toString();
        comment = (version == 1) ? unescape(commentStr) : null;
    }

    protected String unescape(String s) {
        if (s == null) {
            return null;
        }
        if (s.indexOf('\\') == -1) {
            return s;
        }

        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                buf.append(c);
            } else {
                if (++i >= s.length()) {
                    //invalid escape, hence invalid cookie
                    throw new IllegalArgumentException();
                }
                c = s.charAt(i);
                buf.append(c);
            }
        }
        return buf.toString();
    }
}
