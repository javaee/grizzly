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

/**
 *
 * @author gustav trede
 * @since 2009
 */
abstract class WebsocketHandshake implements Handshaker{

   /**
      The value gives the scheme, hostname, and port (if it's not the
      default port for the given scheme) of the page that asked the
      client to open the Web Socket.  It would be interesting if the
      server's operator had deals with operators of other sites, since
      the server could then decide how to respond (or indeed, _whether_
      to respond) based on which site was requesting a connection.
      If the server supports connections from more than one origin, then
      the server should echo the value of this field in the initial*/
    protected final String origin;  
    
    protected int parsed;

    protected TCPIOhandler iohandler;

    protected ByteBuffer bbf;

    public WebsocketHandshake(String origin) {
        this.origin = origin;
    }

    protected void setIOhandler(TCPIOhandler iohandler) {
        this.iohandler = iohandler;
        iohandler.addHandshaker(this);
    }

    protected void handshakeCOMPLETED(){
        this.bbf = null;
    }
    
    protected final static boolean arrayEquals(
            byte[] ba,int bai, int balength,byte[] ref){
        if (balength != ref.length)
            return false;
        for (int i=0;i<ref.length;i++)
            if (ref[i] != ba[bai+i])
                return false;
        return true;
    }

    public final static String createBasicURI(
            int port,boolean secure,String host,String resources){
        return (secure?"wss":"ws")+"://"+
                (secure?(port!=443?":"+port:""):(port!=80?":"+port:""))+
                resources;
    }

    public static class ClientIsnotSecureAsRequired extends HandShakeException{
        public ClientIsnotSecureAsRequired() {
        }
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    public final class WebsockHeader{
        public final String name;
        public final String value;
        public WebsockHeader(String name, String value) {
            this.name = name;
            this.value = value;
        }
        @Override
        public boolean equals(Object obj) {
            return obj instanceof WebsockHeader &&
                    name.equals(((WebsockHeader)obj).name);
        }
        @Override
        public int hashCode() {
            return name.hashCode();
        }
        @Override
        public String toString() {
            return name +":"+value;
        }
    }
}
