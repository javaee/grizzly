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


import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import com.sun.grizzly.samples.migration.QuoteManager;
import com.sun.grizzly.samples.migration.command.ICommandRequest;
import com.sun.grizzly.samples.migration.response.FeedCommandRequest;
import com.sun.grizzly.samples.migration.response.QuoteCommandRequest;
import com.sun.grizzly.samples.migration.response.handler.IResponseHandler;

/**
 * This class simulate the 3th party connection.
 * 
 * Here we send the request to the 3th party and will receive the
 * response.
 * 
 * We simulate the response by a Thread in the responsehandler.  
 * 
 * The thread will generate Random quote.
 * 
 * When the connection is lost with the 3th party, we will
 * reconnect and resend all the feed requested.  
 * 
 * There is a possible problem, is when the 3th party can't handle
 * all the feed in the same time, maybe he can close the connection, 
 * and if that happen, this handler will enter in a loop.
 * 
 * @author Sebastien Dionne
 *
 */
public class ThirdPartyConnectionHandler implements Runnable {
	
	protected QuoteManager f_manager;
	protected String f_host;
	protected int f_sleepDelay = 30000;
	protected int f_reconnectDelay = 500;
	protected boolean f_shutdown = false;
	protected long f_lastActivity = -1;
	
	/**
	 * @return the host
	 */
	public String getHost() {
		return f_host;
	}

	/**
	 * @param host the host
	 */
	public void setHost(String host) {
		f_host = host;
	}


	/**
	 * Shutdown this BridgeConnection.  It will not be possible to restart it !
	 */
	public void shutdown(){
		f_shutdown = true;
	}
	
	/**
	 * Init
	 * @return status if the connection was succesfully made with Bridge
	 */
	public boolean init() {
		return true;
	}

	/**
	 * Resend all the requests to the server, and update the new Request in the cache
	 */
	public void resendQuoteFeedRequest(){
		
		Set<String> quoteSubcriptionList = f_manager.getQuoteSubcriptionList();
		
		if(quoteSubcriptionList!=null){
			
			for (Iterator<String> iterator = quoteSubcriptionList.iterator(); iterator.hasNext();) {
				String quoteSubscription = iterator.next();
				
				
				IResponseHandler responseHandler = f_manager.getResponseHandlerFromQuoteSubcription(quoteSubscription);
				
				if(responseHandler!=null){
					try {
						sendFeedRequest(quoteSubscription, responseHandler);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				
			}
			
		}
		
	}
	
	/**
	 * Reconnect this connection to the 3th party
	 * and all the feed request need to be resend !
	 * @return if the reconnection was successfull
	 */
	public boolean reconnect(){
		
		System.out.println("Reconnecting...");
		
		// close all the thread for BID,ASK because it's a demo
		// in real world, the feed is already lost, because we lost the connection
		
		Set<String> quoteSubcriptionList = f_manager.getQuoteSubcriptionList();
		
		// for each quoteSubcription we need to check if there are other client that are subscribe to this quoteSubcription
		// and we remove this client from the List.
		for (Iterator<String> iterator = quoteSubcriptionList.iterator(); iterator.hasNext();) {
			String quoteSubcription = iterator.next();
			
			 IResponseHandler responseHandler = f_manager.getResponseHandlerFromQuoteSubcription(quoteSubcription);
			 responseHandler.getCommandRequest().close();
		}
		
		boolean success = init();
		
		// resend requestFeed
		if(success){
			resendQuoteFeedRequest();
		}
		
		return success;
	}
	
	/**
	 * Init the connection, and check periodily if we receive activity
	 * in the demo.. we don't keep track on the incoming response, but
	 * in a real application we should monitor the response and keep the latest 
	 * timestamp received and check if the delay between is too big.
	 * 
	 * if the delay is to big, maybe we the 3th party server stop responding, so we
	 * close this connection and reconnect.
	 */
	public void run() {
		
		// init
		if(!init()){
			
			// reconnect
			while(!f_shutdown && !reconnect()){
				try {
					Thread.sleep(f_reconnectDelay);
				} catch (InterruptedException e) {
				}
			}
		}
		
		while(!f_shutdown){
			try {
				
				long now = new Date().getTime();
				
				// we can send ping too..
				if(now-f_lastActivity - f_sleepDelay >0){
					System.out.println("Simulate a disconnection from the 3th party");
					reconnect();
				}
				
				Thread.sleep(f_sleepDelay);
			} catch (InterruptedException e) {
			}
		}
		
	}
	
	/**
	 * Set QuoteManager
	 * @param manager QuoteManager
	 */
	public void setQuoteManager(QuoteManager manager){
		f_manager = manager;
	}
	
	
	/**
	 * Send the request to the 3th party
	 * 
	 * @param quoteSubscription the quote requested
	 * @param responseHandler handler that will received the response
	 * @return CommandRequest request
	 * @throws Exception exception
	 */
	public ICommandRequest sendQuoteRequest(String quoteSubscription, IResponseHandler responseHandler) throws Exception {
		
		QuoteCommandRequest request = new QuoteCommandRequest();
		request.setQuoteSubscription(quoteSubscription);
		request.setResponseHandler(responseHandler);
		
		// we send the query to the third party and we receive notification by the
		// responseHandler .. but here the update are generated in the CommandRequest class in a thread
		// not efficiant.. but it's a DEMo :)  and because it's a QUOTE, we send 
		// the update only to this client
		request.startGenerateQuote();
		
		return request;
	}
	
	/**
	 * Send the request to the 3th party
	 * 
	 * @param quoteSubscription the quote requested
	 * @param responseHandler handler that will received the response
	 * @return CommandRequest request
	 * @throws Exception exception
	 */
	public ICommandRequest sendFeedRequest(String quoteSubscription, IResponseHandler responseHandler) throws Exception {
		
		FeedCommandRequest request = new FeedCommandRequest();
		request.setQuoteSubscription(quoteSubscription);
		request.setResponseHandler(responseHandler);
		
		responseHandler.setCommandRequest(request);
		
		// we send the query to the third party and we receive notification by the
		// responseHandler .. but here the update are generated in the CommandRequest class in a thread
		// not efficiant.. but it's a DEMO :)  and because it's a QUOTE, we send 
		// the update only to this client
		request.startGenerateQuote();
		
		return request;
	}
	
}
