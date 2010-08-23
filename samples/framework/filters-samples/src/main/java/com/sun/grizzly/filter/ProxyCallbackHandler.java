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

package com.sun.grizzly.filter;

/**
 * Enable Proxy support by using a technic called http tunneling.
 * @author John Vieten 16.09.2008
 * @version 1.0
 */

import com.sun.grizzly.*;


import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.io.IOException;

import static com.sun.grizzly.filter.CustomProtocolHelper.logger;
import static com.sun.grizzly.filter.CustomProtocolHelper.log;



abstract public class ProxyCallbackHandler implements CallbackHandler<Context> {
    private static final int BUFFER_SIZE = 4096;
    private static List<String> validReplyMessages = new ArrayList<String>(7);
    {
        validReplyMessages.add("HTTP/1.0 200");
        validReplyMessages.add("HTTP/1.1 200");
        validReplyMessages.add("HP/1.0 200");
        validReplyMessages.add("HTTPS/1.0 200");
    }

    private String host;
    private int port;
    private boolean authentication;
    private String agent;
    private String userName;
    private String userPass;

    private CallbackHandler<Context> callbackhandler;
    private ConnectorHandler connectorHandler;
    private CountDownLatch proxyHandshakeDone;
    private boolean handshakeSuccessfull=false;
    private Exception handshakeException;

    public abstract void onException(String msg, Exception e);

    protected ProxyCallbackHandler(boolean authentication,
                                   CallbackHandler<Context> callbackhandler,
                                   ConnectorHandler connectorHandler,
                                   CountDownLatch proxyHandshakeDone,
                                   String host,
                                   int port,
                                   String agent,
                                   String user,
                                   String pass

    ) {
        this.authentication = authentication;
        this.connectorHandler = connectorHandler;
        this.host = host;
        this.port = port;
        this.callbackhandler = callbackhandler;
        this.proxyHandshakeDone = proxyHandshakeDone;
        this.agent=agent;
        this.userName=user;
        this.userPass=pass;


    }


    public void onConnect(IOEvent<Context> ioEvent) {

        try {
            SelectionKey k = ioEvent.attachment().getSelectionKey();

            try {
                connectorHandler.finishConnect(k);
                log("Proxy finishConnect()");

            } catch (Exception ex) {
                handshakeException=ex;
                onException("Exception in CallbackHandler:", ex);
                return;
            }
            int loop = 0;
            while (loop < 2) {
                if (!sendToProxy()){
                    handshakeException=new Exception("Could not connect to Proxy: "+host+":"+port);
                    return;
                }
                try {
                    boolean tryAgain = receiveFromProxy();
                    if (!tryAgain) break;  //break loop
                    loop++;

                }
                catch (Exception e) {
                    handshakeException=e;
                    onException("Exception receiveFromProxy", e);
                    return;
                }
            }
            ioEvent.attachment().getSelectorHandler().register(k, SelectionKey.OP_READ);
            handshakeSuccessfull=true;
            log("Proxy register OP_READ");
        } finally {
           proxyHandshakeDone.countDown();
        }
    }


    public boolean wasHandshakeSuccessfull() {
        return handshakeSuccessfull;
    }

    public Exception getHandshakeException() {
        return handshakeException;
    }



    public void onRead(IOEvent<Context> ioEvent) {
        callbackhandler.onRead(ioEvent);

    }


    public void onWrite(IOEvent<Context> ioEvent) {
        callbackhandler.onWrite(ioEvent);
    }

    private boolean sendToProxy() {
        try {
            CharsetEncoder encoder;
            encoder = Charset.forName("iso-8859-1").newEncoder();
            String msg = connectProxyMessage(host, port);
            ByteBuffer buf = encoder.encode(CharBuffer.wrap(msg));
            connectorHandler.write(buf, true);
        } catch (Exception e) {
            onException("Exception sendToProxy", e);
            return false;
        }
        return true;
    }

    private boolean receiveFromProxy() throws Exception {

        CharsetDecoder decoder = Charset.forName("iso-8859-1").newDecoder();
        ByteBuffer inBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        connectorHandler.read(inBuffer, true);
        StringBuffer reply = new StringBuffer();

        CharBuffer charBuffer = decoder.decode(inBuffer);

        inBuffer = null;
        reply.append(charBuffer.toString());
        boolean replyOk = false;
        String replyStr = reply.toString();
        if (replyStr == null) {
            throw new IOException("Message Reply was Null");
        }
        logger().log(Level.SEVERE, "replyStr:<start>" + replyStr + "</end>");
        for (String validReplyMessage : validReplyMessages) {
            if (replyStr.startsWith(validReplyMessage)) {
                replyOk = true;
                break;
            }
        }
        if (replyOk) return false;

        if (replyStr.length() > 17 && replyStr.substring(0, 16).indexOf("407") != -1)
            return true;


        throw new IOException("Bad Replystring:" + replyStr);
    }




    private String connectProxyMessage(String host, int port) {
        String msg = "CONNECT " + host + ":" + port + " HTTP/1.0\n";

        msg = msg + "User-Agent: "+agent+" ";
        if (authentication) {
            msg = msg + "\nProxy-Authorization: Basic " + getProxyAuth();
        }
        msg = msg + "\r\n\r\n";

        return msg;
    }
 public  String getProxyAuth() {
        String s = userName + ":" + userPass;

        return base64Encode(s.getBytes());
    }

   	private static String base64Encode(byte[] src) {
		StringBuffer encoded = new StringBuffer();
		byte a, b, c;
		int len = 0;

		for (int i = 0; i < src.length; i += 3) {
			if ((src.length - i) > 3) {
				len = 3;
			}
			else {
				len = src.length - i;
			}

			if (len == 1) {
				a = src[i];
				b = 0;
				c = 0;
				encoded.append(base64Table[(a >>> 2) & 0x3F]);
				encoded.append(base64Table[((a << 4) & 0x30) + ((b >>> 4) & 0xf)]);
				encoded.append('=');
				encoded.append('=');
			}
			else if (len == 2) {
				a = src[i];
				b = src[i + 1];
				c = 0;
				encoded.append(base64Table[(a >>> 2) & 0x3F]);
				encoded.append(base64Table[((a << 4) & 0x30) + ((b >>> 4) & 0xf)]);
				encoded.append(base64Table[((b << 2) & 0x3c) + ((c >>> 6) & 0x3)]);
				encoded.append('=');
			}
			else {
				a = src[i];
				b = src[i + 1];
				c = src[i + 2];
				encoded.append(base64Table[(a >>> 2) & 0x3F]);
				encoded.append(base64Table[((a << 4) & 0x30) + ((b >>> 4) & 0xf)]);
				encoded.append(base64Table[((b << 2) & 0x3c) + ((c >>> 6) & 0x3)]);
				encoded.append(base64Table[c & 0x3F]);
			}
		}

		return encoded.toString();
	}
   	private static char base64Table[] = {
		'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 
		'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
		'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 
		'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 
		'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 
		'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 
		'w', 'x', 'y', 'z', '0', '1', '2', '3', 
		'4', '5', '6', '7', '8', '9', '+', '/'	
	};

}
