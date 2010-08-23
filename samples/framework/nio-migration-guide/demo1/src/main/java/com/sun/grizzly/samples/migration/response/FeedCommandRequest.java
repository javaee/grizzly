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

package com.sun.grizzly.samples.migration.response;

import com.sun.grizzly.samples.migration.dao.Quote;

/**
 * This class simulate a request to the 3th party
 * 
 * The quote are simulated by a Thread in the class.
 * 
 * 
 * @author Sebastien Dionne
 *
 */
public class FeedCommandRequest extends QuoteCommandRequest {
	
	private boolean shutdown = false;
	
	public FeedCommandRequest(){
		super();
	}
	
	@Override
	public void close() {
		shutdown = true;
	}
	
	public void startGenerateQuote(){
		
		Thread t = new Thread(new Runnable(){ public void run(){
			
			while(!shutdown){
				try {
					double bid = 50 * Math.random() + 1;
					double ask = 50 * Math.random() + 1;
					
					// send the generated quote to client
					handleQuote(new Quote(getQuoteSubscription(),bid,ask));
					Thread.currentThread().sleep(500);
				} catch(Exception e){
					e.printStackTrace();
				}
			}
		}
			
		});
		
		// wait 1 sec.. simulate a little delay
		try {
			Thread.sleep(1000);
		} catch(Exception e){
			e.printStackTrace();
		}
		
		t.start();
		
	}

	/*
	 * (non-Javadoc)
	 * @see com.sun.grizzly.samples.migration.response.QuoteCommandRequest#handleQuote(com.sun.grizzly.samples.migration.dao.Quote)
	 */
	public void handleQuote(Quote quote){
		// send the generated quote to client
		StringBuffer sb = new StringBuffer(quote.toString());
		
		getResponseHandler().sendToClient(sb);
		
	}
}
