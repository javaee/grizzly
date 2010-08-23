/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly;

import com.sun.grizzly.Controller.Protocol;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.filter.EchoFilter;
import com.sun.grizzly.utils.ControllerUtils;
import com.sun.grizzly.utils.UDPIOClient;
import java.io.IOException;
import java.util.Arrays;
import junit.framework.TestCase;

/**
 * @author Alexey Stashok
 */
public class UDPControllerTest extends TestCase {
    public static final int PORT = 17519;
    
    public void testDefaultPort() throws IOException {
        final Controller controller = createController(0);
        
        try {
            ControllerUtils.startController(controller);
            int portLowlevel = ((UDPSelectorHandler) controller.getSelectorHandler(Protocol.UDP)).getPortLowLevel();
            int port = ((UDPSelectorHandler) controller.getSelectorHandler(Protocol.UDP)).getPort();
            assertTrue(port > 0 && port == portLowlevel);
        } finally {
            controller.stop();
        }
    }
    
    public void testSimplePacket() throws IOException {
        final Controller controller = createController(PORT);
        
        UDPIOClient client = new UDPIOClient("localhost", PORT);
        
        try {
            byte[] testData = "Hello".getBytes();
            ControllerUtils.startController(controller);
            client.connect();
            client.send(testData);
            byte[] response = new byte[testData.length];
            client.receive(response);
            assertTrue(Arrays.equals(testData, response));
        } finally {
            controller.stop();
            client.close();
        }
    }
    
    private Controller createController(int port) {
        final ProtocolFilter readFilter = new ReadFilter();
        final ProtocolFilter echoFilter = new EchoFilter();
        
        final Controller controller = new Controller();
        UDPSelectorHandler selectorHandler = new UDPSelectorHandler();
        selectorHandler.setPort(port);
        controller.setSelectorHandler(selectorHandler);
        controller.setHandleReadWriteConcurrently(false);
        
        controller.setProtocolChainInstanceHandler(
                new DefaultProtocolChainInstanceHandler(){
            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = protocolChains.poll();
                if (protocolChain == null){
                    protocolChain = new DefaultProtocolChain();
                    protocolChain.addFilter(readFilter);
                    protocolChain.addFilter(echoFilter);
                }
                return protocolChain;
            }
        });
        
        return controller;
    }
}
