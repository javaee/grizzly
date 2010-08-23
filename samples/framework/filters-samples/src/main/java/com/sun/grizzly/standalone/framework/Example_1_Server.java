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

package com.sun.grizzly.standalone.framework;

import com.sun.grizzly.*;
import com.sun.grizzly.filter.*;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.URL;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

/**
 * An example of  How-To write a "Custom Protocol" with Grizzly.
 * Just shows an simple Example of how the CustomProtocol Layer can be used to transfer serialized java Objects.
 * The Example_1_Server goes in pair with Example_1_Client
 *
 * @author John Vieten 21.06.2008
 * @version 1.0
 */
public class Example_1_Server {
    public final static int PORT = 8078;
    public  static enum Call {login,logout,echo,broadcast,loadbalance,message}
    private Server server;
    private SSLConfig sslConfig;
    protected static Logger logger = Logger.getLogger("Example_1_Server");

    public Example_1_Server() {
    }

    public Example_1_Server(SSLConfig sslConfig) {
        this.sslConfig = sslConfig;
    }

    public void init() {

        server = new CustomProtocolServer(8078,this.sslConfig) {
            ServiceHandler serviceHandler = new ServiceHandler();

            public void service(InputStream inputStream, ProtocolOutputStream outputStream, Integer SessionId, Object serverContext) {
                serviceHandler.service(inputStream, outputStream, SessionId, serverContext);
            }
        };

    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    private class ServiceHandler {
        private ConcurrentHashMap<Integer, Integer> sessionMap = new ConcurrentHashMap<Integer, Integer>();

        public void service(InputStream inputStream,
                            ProtocolOutputStream outputStream,
                            Integer session,
                            Object serverContext) {
            try {
                final Context ctx = (Context) serverContext;
                Object[] data = parsePayload(inputStream);
                Call toBeInvoked = (Call)data[0];

                //String methodName = (String) data[0];
                /**
                 * if (badSession(session)) {
                        result = new IllegalAccessException("invalid session");
                        System.out.println("Server : Client has no session");
                    }
                 */



                Object result = null;

                switch(toBeInvoked) {
                    case login:
                    String username = (String) data[1];
                    String pass = (String) data[2];
                    if ("grizzly".equalsIgnoreCase(pass)) {
                        logger.info(msg("Password OK - User: " + username));
                        Object sessionHash = new Object();
                        result = sessionHash.hashCode();
                        sessionMap.put((Integer) result, (Integer) result);
                    }
                    break;
                    case logout:
                        sessionMap.remove(session);
                         logger.info(msg("User logged off"));
                        ctx.getSelectorHandler().getSelectionKeyHandler().cancel(ctx.getSelectionKey());
                        return;
                    case echo:
                        result = data[1];
                        break;
                    case loadbalance:
                        synchronized (Thread.currentThread()) {
                            try {
                                Thread.currentThread().wait(3000);
                            } catch (InterruptedException e) {

                            }

                        }
                        result = Math.PI;
                        break;
                    case broadcast:
                        SelectionKey callerKey=ctx.getSelectionKey();
                        Set<SelectionKey> selectionKeys = ctx.getSelectorHandler().keys();
                        List<SelectionKey> copyList = new ArrayList<SelectionKey>(selectionKeys);
                        for (final SelectionKey key : copyList) {
                                // do not broadcast to caller
                                if(callerKey==key) continue;
                                if (checkConnection(key)) {
                                     OutputStream updateStream = server.getOutputStream(key);
                                     Object broadcastMsg=data[1];
                                     serializeToStream(updateStream,broadcastMsg);
                                }
                        }
                        break;
                     case message:
                        String message=(String) data[1];
                        logger.info(msg(message));
                        return;
                }
                serializeToStream(outputStream,result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private Object[] parsePayload(InputStream stream) throws Exception {
            ObjectInputStream ois = new ObjectInputStream(stream);
            Object[] result = (Object[]) ois.readObject();
            ois.close();
            return result;

        }

        private boolean badSession(int s) {
            return !sessionMap.contains(s);
        }
    }
    private boolean checkConnection(SelectionKey key) {
        if(!key.isValid()) return false;
        if(key.channel() instanceof ServerSocketChannel) return false;
        if(sslConfig!=null){
            // check ssl handshake done

        }
        return true;

    }

    private void serializeToStream(OutputStream outputStream,Object result) throws IOException{
         ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
         objectOutputStream.writeObject(result);
         objectOutputStream.close();
    }

   private String msg(String s) {
         StringBuffer sb = new StringBuffer();
         sb.append("Server");
         sb.append(" : ");
         sb.append(s);
         return sb.toString();
    }

    public static void main(String[] args) {
        Example_1_Server serverExample = new Example_1_Server(MainSSL.createSSLConfig());
        serverExample.init();
        serverExample.start();

    }


}



