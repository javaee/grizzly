/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly;

import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.utils.NullaryFunction;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.SelectionKeyHandler;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import org.glassfish.grizzly.monitoring.MonitoringConfig;

import static junit.framework.Assert.assertEquals;
import org.glassfish.grizzly.attributes.AttributeHolder;

public class TestDefaults {

    @BeforeClass
    public static void init() {
        System.setProperty("org.glassfish.grizzly.DEFAULT_SELECTION_KEY_HANDLER", TestHandler.class.getName());
        System.setProperty("org.glassfish.grizzly.DEFAULT_MEMORY_MANAGER", TestManager.class.getName());
        System.setProperty("org.glassfish.grizzly.DEFAULT_ATTRIBUTE_BUILDER", TestBuilder.class.getName());
                
    }
    
    
    // ------------------------------------------------------------ Test Methods

    
    @Test
    public void testDefaults() throws Exception {
    
        assertEquals(SelectionKeyHandler.DEFAULT_SELECTION_KEY_HANDLER.getClass(), TestHandler.class);
        assertEquals(MemoryManager.DEFAULT_MEMORY_MANAGER.getClass(), TestManager.class);
        assertEquals(AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.getClass(), TestBuilder.class);


    }


    // ---------------------------------------------------------- Nested Classes


    public static class TestHandler implements SelectionKeyHandler {
        @Override
        public void onKeyRegistered(SelectionKey key) {
        }

        @Override
        public void onKeyDeregistered(SelectionKey key) {
        }

        @Override
        public boolean onProcessInterest(SelectionKey key, int interest) throws IOException {
            return false;
        }

        @Override
        public void cancel(SelectionKey key) throws IOException {
        }

        @Override
        public NIOConnection getConnectionForKey(SelectionKey selectionKey) {
            return null; 
        }

        @Override
        public void setConnectionForKey(NIOConnection connection, SelectionKey selectionKey) {
        }

        @Override
        public int ioEvent2SelectionKeyInterest(IOEvent ioEvent) {
            return 0; 
        }

        @Override
        public IOEvent selectionKeyInterest2IoEvent(int selectionKeyInterest) {
            return null;
        }

        @Override
        public IOEvent[] getIOEvents(int interest) {
            return new IOEvent[0]; 
        }
    } // END TestHandler
    
    public static final class TestManager implements MemoryManager {

        @Override
        public Buffer allocate(int size) {
            return null;  
        }

        @Override
        public Buffer allocateAtLeast(int size) {
            return null;  
        }

        @Override
        public Buffer reallocate(Buffer oldBuffer, int newSize) {
            return null;  
        }

        @Override
        public void release(Buffer buffer) {
        }

        @Override
        public boolean willAllocateDirect(int size) {
            return false;
        }

        @Override
        public MonitoringConfig getMonitoringConfig() {
            return null; 
        }
        
    } // END TestManager
    
    public static final class TestBuilder implements AttributeBuilder {
        
        @Override
        public <T> Attribute<T> createAttribute(String name) {
            return null;  
        }

        @Override
        public <T> Attribute<T> createAttribute(String name, T defaultValue) {
            return null;  
        }

        @Override
        public <T> Attribute<T> createAttribute(String name, NullaryFunction<T> initializer) {
            return null;  
        }

        @Override
        public <T> Attribute<T> createAttribute(String name, org.glassfish.grizzly.attributes.NullaryFunction<T> initializer) {
            return null;
        }

        @Override
        public AttributeHolder createSafeAttributeHolder() {
            return null;
        }
        
        @Override
        public AttributeHolder createUnsafeAttributeHolder() {
            return null;
        }
    } // END TestBuilder
}
