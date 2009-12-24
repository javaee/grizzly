/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2009 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.websocket;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;

/** {@inheritDoc} */
public class WebsocketServerHandshake extends WebsocketHandshake{

    private final static byte[] servernormalresponse = Charset.forName(
            "ASCII").encode("HTTP/1.1 101 Web Socket Protocol Handshake\r\n"+
            "Upgrade: WebSocket\r\nConnection: Upgrade\r\n"+
            "WebSocket-Origin:").array();

    private final static byte[] serverlocationresponse = Charset.
            forName("ASCII").encode("\r\nWebSocket-Location:").array();

    private final static byte[] serverprotocolresponse = Charset.
            forName("ASCII").encode("\r\nWebSocket-Protocol:").array();

    private final static byte[] doublelineterminate = new byte[]{13,10,13,10};

    /**
     * Using enum here is not of a great value.
     * But its decent state tracker and fun =)
     */
    private enum HandshakeState{
        PREPAREHANDSHAKERESPONSE{@Override
            public void handshake(WebsocketServerHandshake w) throws Exception {
                w.prepareHhandshakeResponse();
            }},
        WRITEHANDSHAKERESPONSE{@Override
            public void handshake(WebsocketServerHandshake w) throws Exception {
                w.writeHandshakeResponse();
            }},        
        COMPLETED{@Override
            public void handshake(WebsocketServerHandshake w) throws Exception {
                w.handshakeCOMPLETED();
            }};
        public abstract void handshake(WebsocketServerHandshake w)
                throws Exception;
        private final static HandshakeState[] allstates = values();
        public final HandshakeState nextState(){
            return (allstates[this.ordinal()+1]);
        }
    };

    private HandshakeState state = HandshakeState.allstates[0];
   
    private final String serverhostname;

    protected final WebSocketContext ctx;
    
    public WebsocketServerHandshake(String origin,String location,
            String serverhostname,WebSocketContext ctx) {
        super(origin);
        this.serverhostname = serverhostname;
        this.ctx = ctx;
    }

    public boolean doHandshake(ByteBuffer bb) throws Exception{
        state.handshake(this);
        return state == HandshakeState.COMPLETED;
    }

    private void prepareHhandshakeResponse() throws Exception{
        boolean secure = iohandler instanceof SSLIOhandler;
        if (!secure && ctx.isSecureOnly()){ //TODO: improved check needed ?
            throw new ClientIsnotSecureAsRequired();
        }
        final ByteBuffer bb = bbf;
        bb.clear();
        bb.put(servernormalresponse);
        bb.put(origin.getBytes("ASCII"));
        bb.put(serverlocationresponse);
        //TODO: can we cache hostname and port as bytes in websocketcontext, or
        // can those change in runtime ?
        //String location = createBasicURI(
          //      serverport, dosec, serverhostname, ctx.getResourcePath());
        bb.put(((secure?"wss":"ws")+"://"+serverhostname+
                 ctx.getResourcepath()).getBytes("ASCII"));
        String protocol = ctx.getProtocol();
        if (protocol != null){
            bb.put(serverprotocolresponse);
            bb.put(protocol.getBytes("ASCII"));
        }        
        bb.put(doublelineterminate);
        bb.flip();
        (state = state.nextState()).handshake(this);
    }

    private void writeHandshakeResponse() throws Exception{
        final ByteBuffer bb = bbf;
        final TCPIOhandler ioh = iohandler;
        if (ioh.write(bb)){
            bb.clear();
            parsed = 0;
            ioh.initialInterest(SelectionKey.OP_READ);
            (state = state.nextState()).handshake(this);
        }else{
            ioh.initialInterest(SelectionKey.OP_WRITE);
        }
    }

    @Override
    public String toString() {
        return WebsocketServerHandshake.class.getSimpleName()
                +" "+state
                +" parsed:"+parsed               
                ;
    }
}
