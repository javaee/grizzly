/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy.frames;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.MemoryManager;

public class SettingsFrame extends SpdyFrame {

    private static final ThreadCache.CachedTypeIndex<SettingsFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(SettingsFrame.class, 8);

    public static final int TYPE = 4;
    public static final byte FLAG_SETTINGS_PERSIST_VALUE = 0x01;
    public static final byte FLAG_SETTINGS_PERSISTED = 0x02;

    private int numberOfSettings;
    private Buffer settings;

    // ------------------------------------------------------------ Constructors


    protected SettingsFrame() {
        super();
    }


    // ---------------------------------------------------------- Public Methods


    static SettingsFrame create() {
        SettingsFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new SettingsFrame();
        }
        return frame;
    }

    static SettingsFrame create(final SpdyHeader header) {
        SettingsFrame frame = create();
        frame.initialize(header);
        return frame;
    }

    public static SettingsFrameBuilder builder() {
        return new SettingsFrameBuilder();
    }

    public int getNumberOfSettings() {
        return numberOfSettings;
    }

    public Buffer getSettings() {
        return settings;
    }

    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        numberOfSettings = 0;
        settings = null;
        super.recycle();
        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // -------------------------------------------------- Methods from SpdyFrame


    @Override
    public Buffer toBuffer(MemoryManager memoryManager) {
        return null;
    }


    // ------------------------------------------------------- Protected Methods

    @Override
    protected void initialize(SpdyHeader header) {
        super.initialize(header);
        numberOfSettings = header.buffer.getInt();
        settings = header.buffer;
    }


    // ---------------------------------------------------------- Nested Classes


    public static class SettingsFrameBuilder extends SpdyFrameBuilder<SettingsFrameBuilder> {

        private SettingsFrame settingsFrame;


        // -------------------------------------------------------- Constructors


        protected SettingsFrameBuilder() {
            super(SettingsFrame.create());
            settingsFrame = (SettingsFrame) frame;
        }


        // ------------------------------------------------------ Public Methods


        public SettingsFrame build() {
            return settingsFrame;
        }

        // --------------------------------------- Methods from SpdyFrameBuilder


        @Override
        protected SettingsFrameBuilder getThis() {
            return this;
        }

    } // END SettingsFrameBuilder

}
