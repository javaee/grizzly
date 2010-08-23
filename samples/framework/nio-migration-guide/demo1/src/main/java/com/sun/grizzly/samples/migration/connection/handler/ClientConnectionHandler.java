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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import com.sun.grizzly.samples.migration.QuoteManager;

/**
 * This class contain the logic to parse the request
 * from the client.
 * 
 * There is one Thread by client connected.
 * 
 * @author Sebastien Dionne
 *
 */
public class ClientConnectionHandler implements Runnable {
	
	protected QuoteManager f_manager;
	protected SocketChannel f_socketChannel;
	
	// the limit, if the limit is reach the connection will be closed
	protected static final int LIMITBB = 15000;
	protected CharsetDecoder f_asciiDecoder = Charset.forName("ISO-8859-1").newDecoder();
	protected ByteBuffer f_cumulatifBB = ByteBuffer.allocate(1000);
	
	// Create a direct buffer to get bytes from socket.
    // Direct buffers should be long-lived and be reused as much as possible.
	protected ByteBuffer f_buf = ByteBuffer.allocateDirect(1000);
	
	protected boolean f_shutdown = false;
	
	/**
	 * manage the connection with the client
	 * @param manager le QuoteManager
	 */
	public ClientConnectionHandler(QuoteManager manager){
		f_manager = manager;
	}
	
	/**
	 * close the client connection
	 */
	public synchronized void close(){
		
		System.out.println("ClientConnection close");
		if(f_shutdown){
			return;
		}
		 // No more bytes can be read from the channel
    	try {
			f_socketChannel.close();
		} catch (IOException e) {
			//getting java.nio.channels.AsynchronousCloseException
			e.printStackTrace();
		}
		
		// unsubcribe to quotefeed
		//f_manager.unRegisterClientConnectionHandler(this);
		f_manager.unsubcribeClient(this);
		
		f_shutdown = true;
	}
	
	
	/**
	 * 
	 * @return socketChannel
	 */
	public SocketChannel getSocketChannel(){
		return f_socketChannel;
	}
	
	/**
	 * 
	 * @param socketChannel socket channel
	 */
	public void setSocketChannel(SocketChannel socketChannel){
		f_socketChannel = socketChannel;
	}

	/**
	 * parse the request from the client
	 */
	public void run() {
		
		while(f_socketChannel.isOpen()){
			
		    try {
		    	String query = getQuery(f_buf);
		    	
		    	if(query!=null){
		    		System.out.println("query found [" + query + "]");
		    		f_manager.processQuery(this, query);
		    	}
		    	
		    } catch (Exception e) {
		        // Connection may have been closed
		    	e.printStackTrace();
		    	// CLOSE
		    	close();
		    }
		}
		
	}
	
	/**
	 * find the query formatted : xxx|symbol[eoq]
	 * 
	 * the buffer could contain more than one query.
	 * 
	 * ex : msg1 = quote|aaa[eoq]
	 *    : msg2 = feed|bbb[eoq]_zzzzz
	 *
	 * The first query will be return and the rest will be kept in the buffer
	 * for the next pass.  
	 * 
	 * @param buf buf
	 * @return the query found
	 * @throws IOException exception
	 */
	public String getQuery(ByteBuffer buf) throws IOException {
		
		while(f_socketChannel.isConnected()){
			
			// check if there is already a request in the buffer
			String query = parseQuery();
			if(query!=null){
				return query;
			}
			
	    	// Clear the buffer and read bytes from socket
	        buf.clear();
	        
	        int numBytesRead = f_socketChannel.read(buf);
	    
	        if (numBytesRead == -1) {
	            // No more bytes can be read from the channel
	        	close();
	        } else {
	            // To read the bytes, flip the buffer
	            buf.flip();
	    
	            if(buf.hasRemaining()){
	            	
	            	//System.out.println("Remaining=" + buf.remaining());
	            	
	            	// on remplit le BB cumulatifBB avec le BB buf
	            	f_cumulatifBB = getBB(f_cumulatifBB, buf);
	            	
	            	// parseQuery
	            	return parseQuery();
	            		
	            }
	        }
		}
	        
		return null;
	}
	
	/**
	 * Parse the buffer and look for a valid query
	 * @return a Query
	 * @throws IOException exception
	 */
	protected String parseQuery() throws IOException{
	
		String query = null;
		
		if(f_cumulatifBB.hasRemaining()){
			// On lit le buffer cumulatif pour voir si on trouverait la EOQ
	    	ByteBuffer tmp = f_cumulatifBB.duplicate();
	    	tmp.flip();
	    	
	    	// decode the buffer
	    	String msg = f_asciiDecoder.decode(tmp).toString();
	    	
	    	//System.out.println("msg=" + msg);
	    	
	    	int index = msg.indexOf("[eoq]");
	    	if(index>-1){
	    		
	    		query = msg.substring(0,index);
	    		//System.out.println("Query = " + query);
	    		
	    		// We need to kept what is after the EOQ
	    		f_cumulatifBB.clear();
	    		f_cumulatifBB.put(msg.substring(index + "[eoq]".length()).getBytes());
	    	} else {
	    		//System.out.println("no EOQ in this iteration");
	    	}
		}
    	
    	return query;
	}
	
	/**
	 * 
	 * @param cumulatifBB bb 
	 * @param buf buf
	 * @return BB
	 */
	public ByteBuffer getBB(ByteBuffer cumulatifBB, ByteBuffer buf){
		// on valide que le buffer n'explosera pas
    	if(cumulatifBB.position() + buf.remaining()>=cumulatifBB.capacity()){
    		// on regarde si nous n'avons pas la valeur maximale pour le buffer
    		if(cumulatifBB.capacity() + buf.remaining()<LIMITBB){
    			
    			ByteBuffer bb = ByteBuffer.allocate(cumulatifBB.capacity() + buf.capacity());
    			cumulatifBB.flip();
    			bb.put(cumulatifBB);
    			cumulatifBB = bb;
    			
    		} else {
    			// on avons atteind le maximum, donc on ferme la connection
    			
    			System.out.println("BUFFER MAX ATTEIND.. LE CLIENT ENVOYE DE LA JUNK OU LE BUFFER EST TROP PETIT!");
    		}
    	}
    	
    	cumulatifBB = cumulatifBB.put(buf);
    	
    	return cumulatifBB;
	}
	
}
