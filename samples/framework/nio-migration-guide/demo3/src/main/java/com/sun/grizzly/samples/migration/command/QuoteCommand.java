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

package com.sun.grizzly.samples.migration.command;

import com.sun.grizzly.samples.migration.QuoteManager;
import com.sun.grizzly.samples.migration.connection.handler.ClientConnectionHandler;
import com.sun.grizzly.samples.migration.connection.handler.ThirdPartyConnectionHandler;
import com.sun.grizzly.samples.migration.response.handler.IResponseHandler;
import com.sun.grizzly.samples.migration.response.handler.QuoteResponseHandler;

/**
 * The quote Command must be in the format
 * 
 * quote|symbol[eoq]
 * 
 * The server will return the latest quote from
 * the cache, if there is a feed subscribe for this
 * symbol.  If not, the server will ask the quote at
 * the 3th party, and when the response is received,
 * it will be send to be client.
 * 
 * Once the client received the response, the client 
 * connection will be closed.
 * 
 * @author Sebastien Dionne
 *
 */
public class QuoteCommand extends BasicCommand {
	
	public QuoteCommand(QuoteManager manager) {
		super(manager);
	}

	/**
	 * process the query
	 * @param query query to send to 3th party
	 * @param clientConnectionHandler client that call the command
	 * @return CommandRequest the request 
	 */
	public ICommandRequest process(String query, ClientConnectionHandler clientConnectionHandler){
		
		// extract the quote from the query
		int index = query.indexOf(QuoteManager.ARG_SEP);
		if(index<=0){
			//error
			return null;
		}
		
		f_quoteSubscription = query.substring(index+QuoteManager.ARG_SEP.length());
		
		f_responseHandler = new QuoteResponseHandler();
		f_responseHandler.setClientConnectionHandler(clientConnectionHandler);
		f_responseHandler.setCommand(this);
		
		// look if the quoteSubscription is in the cache
		IResponseHandler handler = f_manager.getResponseHandlerFromQuoteSubcription(f_quoteSubscription);
		if(handler!=null){
			System.out.println("QUOTE FOUND IN CACHE = " + f_quoteSubscription);
			
			handler.sendLastUpdateToClient(clientConnectionHandler);
			return handler.getCommandRequest();
		} 
		
		ThirdPartyConnectionHandler connection = f_manager.getThirdPartyConnectionHandler();
		
		if (connection == null) {
			return null;
		}

		// Create a new request to the 3th party
		try {
			return connection.sendQuoteRequest(f_quoteSubscription, f_responseHandler);
		} catch (Exception e) {
			// here we could send a message to the client.. but it's a demo
			System.out.println(e);
		}
		
		return null;
	}

}
