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

package com.sun.grizzly.samples.migration;


/**
 * 
 * This project is a little real-time quote stock server.
 * 
 * The goal of this project was to create a demo and 
 * a migration guide : "How to convert NIO server to Grizzly.
 * 
 * A client can ask for a quote or a quote feed (the client will receive all update on the stock)
 * 
 * This server simulate a 3th party connection (where it will get the quotes for the symbols).
 * 
 * When this server lost the connection will the 3th party, it will reconnect and resend all the
 * quote feed request to the 3th party.
 * 
 * In a real application the server should be able to handle this : 
 * 
 * 50 clients simultany and 5000 symbols cached.  Of theses clients, there will be 5 admin (they will receive
 * all the symbols updates in realtime). There will be around 20 clients that will ask for 100 symbols quote feed.
 * And the others clients, will ask for a quote and disconnect after that.  We can expect have a request each second
 * from theses clients.
 * 
 * Remarks : 
 * 
 * The server will listen on a TCP port for incoming client.  A thread will be created for each client.
 * The server had a limited buffer size for parsing the client request.  If the buffer limit is reach, 
 * the server will close the connection with the client.
 * 
 * When a client request a quote, the server will return the latest quote from it cache if there was a 
 * quote feed requested.  When the client disconnect from the server, the server will stop the quote feed 
 * if there is no more client subscribe to the feed.  
 * 
 * When a client disconnect the server throws a java.nio.channels.AsynchronousCloseException.
 * 
 * When the server lost the connection with the 3th party, it will resend all the quote feed, but if the
 * 3th party can't handle all the requests in the same time, it could close the connection, and we will be 
 * in a loop.  The server will resend all the request, and the 3th party will reclose the connection.
 * 
 * @author Sebastien Dionne
 *
 */
public class GrizzlyGateway {

	private QuoteManager manager = null;
	private Thread managerThread = null;
	
    /**
     * Le traitement principal du programme
     */
    protected void process() {

    	manager = new QuoteManager();
    	
    	managerThread = new Thread(manager,"quoteManager");
    	
    	managerThread.start();
    }
    
	/**
	 * @param args les arguments au programme
	 */
	public static void main(String[] args) {
		
		GrizzlyGateway a = new GrizzlyGateway();
		
		System.out.println("GrizzlyGateway started");
		
		a.process();
		
	}

}
