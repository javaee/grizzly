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

import com.sun.grizzly.filter.*;
import com.sun.grizzly.SSLConfig;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * An example of  How-To write a "Custom Protocol" with Grizzly.
 * Just shows an simple Example of how the CustomProtocol Layer can be used.
 * This Example_1_Server goes in pair with this Example_1_Client
 *
 * @author John Vieten 21.06.2008
 * @version 1.0
 */
public class Example_1_Client {
    private Client client;
    private CountDownLatch finishedLatch;
    private String name;
    private SSLConfig sslConfig;
    protected static Logger logger = Logger.getLogger("Example_1_Client");
    private boolean shutDown=false;



    public Example_1_Client(String name,CountDownLatch finishedLatch) {
        this.name = name;
        this.finishedLatch=finishedLatch;

    }
     public Example_1_Client(String name,CountDownLatch finishedLatch,SSLConfig sslConfig) {
        this.name = name;
        this.finishedLatch=finishedLatch;
        this.sslConfig=sslConfig;
    }



    public void start() throws Exception {
        client = new CustomProtocolClient(sslConfig){
            @Override
            public void service(InputStream inputStream, OutputStream outputStream) {
               try {
                    ObjectInputStream ois =new ObjectInputStream(inputStream);
                    Object obj = ois.readObject();
                    ois.close();
                    String message=(String)obj;
                    logger.info(msg("Recieved Broadcast :"+message));
                    if(!shutDown) {
                        write(new Object[]{Example_1_Server.Call.message,msg("Answer - Good thank you!")},outputStream);
                    } else{
                         logger.info(msg("******************** No write Connection closed ******************"));
                    }


                } catch (Exception e) {
                    CustomProtocolHelper.logger().log(Level.SEVERE, "onRequestMessage()", e);
                }
            }
        };
       client .setIoExceptionHandler(new IOExceptionHandler() {
           public void handle(IOException ex) {
               ex.printStackTrace();
           }
       });

       client.start();
        
       client.connect(new InetSocketAddress("localhost", Example_1_Server.PORT));

        try {
            useCaseSimpleLogin();
        } catch (Exception e) {
            logger.info(" Has  Server been started?");
            e.printStackTrace();
        }

        useCaseEcho();

        for (int i = 0; i < 50; i++) {
            boolean resultOk =  useCaseEchoLargeObject();

            if(!resultOk) {
               logger.info("Attention Client "+name+" : useCaseEchoLargeObject : Bad Result : ");
            }


        }
        logger.info(msg("useCaseEchoLargeObject done"));

       // broadcast();

        useCaseServerTakesSimeTime();

        useCaseLogout();
        
        stop();
        
        if(finishedLatch!=null) finishedLatch.countDown();
        
       
    }
    public void stop() throws Exception{
        
        client.stop();
        logger.info(msg("stopped"));
    }
    


    public static void main(String[] args) throws Exception{
        Example_1_Client example_1_client = new Example_1_Client("Main",null,MainSSL.createSSLConfig());
        example_1_client.start();
    }

    void useCaseSimpleLogin() throws Exception {
        Object result = sendMessage(Example_1_Server.Call.login, "John "+name, "grizzly");
        if (result == null) {
            throw new Exception("Could not login");
        }
        Integer session = (Integer) result;
        logger.info(msg("got session :" + session));
        ((CustomProtocolClient)client).setSession(session);

    }

    void useCaseLogout() throws Exception {

        write(new Object[]{Example_1_Server.Call.logout, ""}, client.getOutputStream());
        shutDown=true;
        logger.info(msg("done"));
    }


    void useCaseEcho() throws Exception {
        String echoStr = "echo grizzly";
        Object result = sendMessage(Example_1_Server.Call.echo, echoStr);
        logger.info(msg("correct :" + result.equals(echoStr)));


    }


    boolean useCaseEchoLargeObject() throws Exception {

        String[] lines = new String[1001];
        for (int i = 0; i < lines.length; i++) {
            String tmp = new String("Test a bigger Payload 123456");
            lines[i]=(i%2==0)?tmp:tmp.toUpperCase();
        }

        String[] result = (String[]) sendMessage(Example_1_Server.Call.echo, lines);
        boolean resultOk=true;
         for (int i = 0; i < lines.length; i++) {
            if(!result[i].equals(lines[i])) {
                logger.info("Bad result line :"+i);
                resultOk=false;
            }
        }
        return resultOk;


    }

    void broadcast() throws Exception {
        sendMessage(Example_1_Server.Call.broadcast, msg("Hello, How do you do?"));
        logger.info(msg("done"));
    }

    void useCaseServerTakesSimeTime() throws Exception {
       Object result = sendMessage(Example_1_Server.Call.loadbalance, "");
       logger.info( msg("Received time consuming result Result PI : " + result));
    }

    private Object sendMessage(Object... data) throws Exception {
        RemoteCall holder = client.callRemote();
        write(data, holder.getOutputStream());
        ObjectInputStream ois = new ObjectInputStream(holder.getInputStream());
        Object result = ois.readObject();
        ois.close();
        if(result instanceof IllegalAccessException) {
            throw (IllegalAccessException)result;
        }
        return result;
    }

    public void write(final Object params[], OutputStream stream) throws IOException {
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(stream);
            oos.writeObject(params);
        } finally {
            if (oos != null) oos.close();
        }
    }

    private String msg(String s) {
         StringBuffer sb = new StringBuffer();
         sb.append("Client ");
         sb.append(name);
         sb.append(" : ");
         sb.append(s);
         return sb.toString();
    }



}
