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

package com.sun.grizzly.samples.migration.connection.handler;


import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;


import com.sun.grizzly.BaseSelectionKeyHandler;
import com.sun.grizzly.Controller;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolChainInstanceHandler;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.samples.migration.QuoteManager;
import com.sun.grizzly.samples.migration.connection.handler.filter.QuoteQueryManagerFilter;
import com.sun.grizzly.samples.migration.connection.handler.filter.QuoteQueryProtocolFilter;

/**
 * This class listen for incoming connection
 * 
 * @author Sebastien Dionne
 *
 */
public class GrizzlyConnectionListener implements Runnable {
	
	protected String f_name;
	protected int f_port;
	protected QuoteManager f_quoteManager;
	protected boolean f_shutdown;
	protected ThreadGroup f_threadGroup;
	protected ServerSocketChannel f_serverSocket;
	protected ServerSocketChannel f_serverSocketChannel;
	protected Selector f_selector;
	
	protected Controller f_controller;
	
	/**
	 * @return the quoteManager
	 */
	public QuoteManager getQuoteManager() {
		return f_quoteManager;
	}

	/**
	 * @param quoteManager the quoteManager
	 */
	public void setQuoteManager(QuoteManager quoteManager) {
		this.f_quoteManager = quoteManager;
	}
	
	/**
	 * 
	 * @param port port for the incomming connection
	 */
	public void setPort(int port){
		f_port = port;
	}
	
	/**
	 * 
	 * @return the listening port
	 */
	public int getPort(){
		return f_port;
	}

	/**
	 * Init
	 */
	public void init(){
		
		System.out.println("listening for incomming TCP Connections on port : " + f_port);
		try {
			
			f_controller = new Controller();
			TCPSelectorHandler tcpSelectorHandler = new TCPSelectorHandler();
			tcpSelectorHandler.setPort(f_port);

       tcpSelectorHandler.setSelectionKeyHandler(new BaseSelectionKeyHandler());
       
       f_controller.addSelectorHandler(tcpSelectorHandler);
       
       QuoteQueryProtocolFilter protocolParser = new QuoteQueryProtocolFilter();
       QuoteQueryManagerFilter quoteManagerFilter = new QuoteQueryManagerFilter(f_quoteManager);
       
       final ProtocolChain protocolChain = new DefaultProtocolChain();
       protocolChain.addFilter(protocolParser);
       protocolChain.addFilter(quoteManagerFilter);
       ((DefaultProtocolChain) protocolChain).setContinuousExecution(true);


       ProtocolChainInstanceHandler pciHandler = new DefaultProtocolChainInstanceHandler() {

           public ProtocolChain poll() {

               return protocolChain;
           }

           public boolean offer(ProtocolChain protocolChain) {
               return false;

           }
       };

       f_controller.setProtocolChainInstanceHandler(pciHandler);
       try {
    	   f_controller.start();
       } catch (IOException e) {
           e.printStackTrace();
       }
           
			
		} catch (Exception e) {
			System.exit(-10);
		}
	}

	/**
	 * @return the shutdown
	 */
	public boolean isShutdown() {
		return f_shutdown;
	}

	/**
	 * shutdown the socket listener
	 */
	public void shutdown() {
		f_shutdown = true;
		// A TESTER
		f_threadGroup.interrupt();
	}
	
	/**
	 * processing
	 * 
	 * - Open socket
	 * - a new Thread (ClientConnectionHandler)
	 * - start the new thread
	 */
	public void run() {

		init();
		
		
	}
	
	
}
