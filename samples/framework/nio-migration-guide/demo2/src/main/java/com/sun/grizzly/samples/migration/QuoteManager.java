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


import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

import com.sun.grizzly.samples.migration.command.FeedCommand;
import com.sun.grizzly.samples.migration.command.ICommand;
import com.sun.grizzly.samples.migration.command.QuitCommand;
import com.sun.grizzly.samples.migration.command.QuoteCommand;
import com.sun.grizzly.samples.migration.connection.handler.ClientConnectionHandler;
import com.sun.grizzly.samples.migration.connection.handler.SocketConnectionListener;
import com.sun.grizzly.samples.migration.connection.handler.ThirdPartyConnectionHandler;
import com.sun.grizzly.samples.migration.response.handler.IResponseHandler;

/**
 * This class contain the business logic of the application.
 * 
 * All the caches are kept here.  
 * 
 * @author Sebastien Dionne
 *
 */
public class QuoteManager implements Runnable {

	public static final String ARG_SEP = "|";
	public static final String EOQ = "[eoq]";
	
	public static final int INCOMING_PORT = 5000;
	protected ThreadGroup f_tg = null;
	
	
	// connections
	private ThirdPartyConnectionHandler thirdConnection = null;
	private SocketConnectionListener incomingConnectionListener = null;
	
	
	// CACHE for the quoteSubscription feed requested.  The IResponseHandler receive the bid/ask update
	private ConcurrentMap<String, IResponseHandler> quoteSubscriptionCache = null;
	
	// CACHE for keeping trace of which clients subcribed to which quotesubcription
	private ConcurrentMap<String, List<ClientConnectionHandler>> clientHandlerByQuoteSubcriptionCache = null;
	
	// CACHE for keeping trace of what the client is subscribed to.  see clientHandlerByQuoteSubcriptionCache
	private ConcurrentMap<ClientConnectionHandler, ConcurrentSkipListSet<String>> clientSubcriptionListCache;
	
	/**
	 * Initialisation
	 * 
	 * The 3th party connection is established and 
	 * the incoming connection listener.
	 */
	protected void init(){
		
		quoteSubscriptionCache = new ConcurrentHashMap<String, IResponseHandler>();
		clientHandlerByQuoteSubcriptionCache = new ConcurrentHashMap<String, List<ClientConnectionHandler>>();
		clientSubcriptionListCache = new ConcurrentHashMap<ClientConnectionHandler, ConcurrentSkipListSet<String>>();
		
		f_tg = new ThreadGroup("Connection IN/OUT");
		
		// 3th party connection
		thirdConnection = new  ThirdPartyConnectionHandler();
		thirdConnection.setQuoteManager(this);
		
		new Thread(f_tg, thirdConnection, "ThirdPartyConnectionHandler").start();
		
		// incoming connection
		incomingConnectionListener = new SocketConnectionListener();
		incomingConnectionListener.setQuoteManager(this);
		incomingConnectionListener.setPort(INCOMING_PORT);
		
		// start the listening service
		new Thread(f_tg, incomingConnectionListener, "Incoming Connection").start();
		
	}
	
	/**
	 * the startup.  Here it's a demo, so this 
	 * function is useless and we don't support the 
	 * shutdown for the application, but it could be handle here
	 */
	public void run() {
		
		init();
		
	}
	
	/**
	 * 
	 * @return the 3th party connection
	 */
	public ThirdPartyConnectionHandler getThirdPartyConnectionHandler(){
		return thirdConnection;
	}
	
	/**
	 * Return a class that will handle the command
	 * requested by the client
	 * 
	 * The supported commands are : quit, quote, feed
	 *  
	 * @param commandName name of the command
	 * @return the class that will handle the command
	 */
	public ICommand getCommand(String commandName){
		
		// we could use Spring to load the config, but we are in a demo :)
		
		if(commandName.equalsIgnoreCase("quit")){
			return new QuitCommand(this);
		} else if(commandName.equalsIgnoreCase("quote")){
			return new QuoteCommand(this);
		} else if(commandName.equalsIgnoreCase("feed")){
			return new FeedCommand(this);
		}
		
		return null;
	}
	
	/**
	 * Process the request from the client.
	 * 
	 * @param clientHandler the connection from the client
	 * @param query the query
	 */
	public void processQuery(ClientConnectionHandler clientHandler, String query){
		
        // extract the command
        int index = query.indexOf(ARG_SEP);
        String commandName = query.substring(0,index);
        

        ICommand command = getCommand(commandName);
        
        if(command==null){
        	System.out.println("Commande non supportee : commandName = [" + commandName + "]");
        	return;
        }
        
        // send the query
        command.process(query, clientHandler);
        
	}
	
	/**
	 * @return the quoteSubscription feed list requested
	 */
	public Set<String> getQuoteSubcriptionList(){
		return quoteSubscriptionCache.keySet();
	}
	
	/**
	 * @return the IResponseHandler receive the bid/ask update
	 */
	public IResponseHandler getResponseHandlerFromQuoteSubcription(String quoteSubcription){
		return quoteSubscriptionCache.get(quoteSubcription);
	}
	
	/**
	 * Add the quoteSubscription to the cache
	 * 
	 * @param quoteSubcription the symbol that we want to receive quote
	 * @param responseHandler the handler that will receive update
	 */
	public void addQuoteSubcription(String quoteSubcription, IResponseHandler responseHandler){
		quoteSubscriptionCache.put(quoteSubcription, responseHandler);
	}
	
	/**
	 * 
	 * @param quoteSubcription the symbol that we want to receive quote
	 * @return list of ClientConnectionHandler that are subscribe to the quoteSubscription
	 */
	public List<ClientConnectionHandler> getClientHandlerByQuoteSubcription(String quoteSubcription){
		return clientHandlerByQuoteSubcriptionCache.get(quoteSubcription);
	}
	
	/**
	 * 
	 * @param clientConnectionHandler client connection handler
	 * @return list of quoteSubcription for this client
	 */
	public ConcurrentSkipListSet<String> getQuoteSubscription(ClientConnectionHandler clientConnectionHandler){
		return clientSubcriptionListCache.get(clientConnectionHandler);
	}
	
	/**
	 * Add the quoteSubcription in the cache for this clientConnectionhandler
	 * @param quoteSubcription the symbol that we want to receive quote
	 * @param clientConnectionHandler client connection handler
	 */
	public void addQuoteSubcriptionForClientConnectionHandler(String quoteSubcription, ClientConnectionHandler clientConnectionHandler){
		
		List<ClientConnectionHandler> list = getClientHandlerByQuoteSubcription(quoteSubcription);
		
		if(list==null){
			list = new Vector<ClientConnectionHandler>();
			clientHandlerByQuoteSubcriptionCache.put(quoteSubcription, list);
		}
		
		list.add(clientConnectionHandler);
		
		ConcurrentSkipListSet<String> listClientSubscripion = clientSubcriptionListCache.get(clientConnectionHandler);
		
		if(listClientSubscripion==null){
			listClientSubscripion = new ConcurrentSkipListSet<String>();
			clientSubcriptionListCache.put(clientConnectionHandler, listClientSubscripion);
		}
		
		listClientSubscripion.add(quoteSubcription);
		
	}
	
	/**
	 * unsubscribe all the quoteSubcription for this client.  If there is no more client subcribe
	 * to a quoteSubcripotion, the subcription will be close.
	 * @param clientConnectionHandler the client connection handler
	 */
	public void unsubcribeClient(ClientConnectionHandler clientConnectionHandler){
		
		ConcurrentSkipListSet<String> quoteSubcriptionList = getQuoteSubscription(clientConnectionHandler);
		
		// for each quoteSubcription we need to check if there are other client that are subcribe to this quoteSubcription
		// and we remove this client from the List.
		for (Iterator<String> iterator = quoteSubcriptionList.iterator(); iterator.hasNext();) {
			String quoteSubcription = iterator.next();
			
			 List<ClientConnectionHandler> listClient = getClientHandlerByQuoteSubcription(quoteSubcription);
			 listClient.remove(clientConnectionHandler);
			 
			 // if empty, the quoteSubcription is not needed anymore, so we close it
			 if(listClient.size()==0){
				 IResponseHandler responseHandler = getResponseHandlerFromQuoteSubcription(quoteSubcription);
				 
				 responseHandler.getCommandRequest().close();
				 
				 quoteSubscriptionCache.remove(quoteSubcription);
				 
			 }
			 
		}
	}

}
