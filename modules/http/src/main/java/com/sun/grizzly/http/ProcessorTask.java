/*
 * The contents of this file are subject to the terms 
 * of the Common Development and Distribution License 
 * (the License).  You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at 
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing 
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL 
 * Header Notice in each file and include the License file 
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.  
 * If applicable, add the following below the CDDL Header, 
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.http;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import com.sun.grizzly.util.Interceptor;

/**
 * Process HTTP request. 
 *
 * @author Jean-Francois Arcand
 */
public interface ProcessorTask extends Task{
 
    /**
     * Initialize the stream and the buffer used to parse the request.
     */
    public void initialize();

    
    /**
     * Return the request input buffer size
     */
    public int getBufferSize();

    
    /**
     * Is the keep-alive mechanism enabled or disabled.
     */
    public boolean getDropConnection();

    

    /**
     * Return the maximum size of a POST which will be buffered in SSL mode.
     */
    public int getMaxPostSize();

    
    /**
     * Return the current <code>Socket</code> used by this instance
     * @return socket the current <code>Socket</code> used by this instance
     */
    public Socket getSocket();

    
    /**
     * Invoke the <code>Adapter</code>, which usualy invoke the Servlet
     * Container.
     */
    public void invokeAdapter();

    
    /**
     * Parse the request line and the http header.
     */
    public void parseRequest() throws Exception;

    
    /**
     * Parse the request line and the http header.
     * @param input the InputStream to read bytes
     * @param output the OutputStream to write bytes
     * @return true if the parsing was successful.
     */
    public boolean parseRequest(InputStream input, OutputStream output, 
                                boolean keptAlive) throws Exception;
   
    
    /**
     * Post process the http request, after the response has been
     * commited.
     */
    public void postProcess() throws Exception;

    
    /**
     * Post process the http request, after the response has been
     * commited.
     */
    public void postProcess(InputStream input, OutputStream output) 
        throws Exception;
    
    
    /**
     * Prepare and post the response.
     * @param input the InputStream to read bytes
     * @param output the OutputStream to write bytes
     */
    public void postResponse() throws Exception;

    
    /**
     * Pre process the request by decoding the request line and the header.
     */
    public void preProcess() throws Exception;

    
    /**
     * Pre process the request by decoding the request line and the header.
     * @param input the InputStream to read bytes
     * @param output the OutputStream to write bytes
     */
    public void preProcess(InputStream input, OutputStream output) 
        throws Exception;

    
    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     * 
     * @param input stream from which the HTTP requests will be read
     * @param output stream which will be used to output the HTTP
     * responses
     * @return true is an error occured.
     * @throws Exception error during an I/O operation
     */
    public boolean process(InputStream input, OutputStream output) 
        throws Exception;

    
    /**
     * Set the request input buffer size
     */
    public void setBufferSize(int requestBufferSize);

   
    /**
     * Enable or disable the keep-alive mechanism. Setting this value
     * to <code>false</code> will automatically add the following header to the
     * response ' Connection: close '
     */
    public void setDropConnection(boolean dropConnection);

    
    /**
     * Set the <code>Interceptor</code> used by this instance.
     */
    public void setHandler(Interceptor handler);

    
    /**
     * Get the <code>Interceptor</code> used by this instance.
     */
    public Interceptor getHandler();
    
    
    public void setMaxHttpHeaderSize(int maxHttpHeaderSize);

    
    /**
     * Set the maximum size of a POST which will be buffered in SSL mode.
     */
    public void setMaxPostSize(int mps);

    
    /**
     * Set the socket associated with this HTTP connection.
     */
    public void setSocket(Socket socket);

    
    /**
     * Set the <code>InputStream</code> associated with HTTP connection
     */
    public void setInputStream(InputStream inputStream);

    /**
     * Get the <code>InputStream</code> associated with HTTP connection
     */
    public InputStream getInputStream();
    
    /**
     * Set the upload timeout.
     */
    public void setTimeout(int timeouts);

    
    /**
     * Notify the <code>TaskListener</code> that the request has been 
     * fully processed.
     */
    public void terminateProcess();
    
    
    /**
     * Return the request URI.
     */
    public String getRequestURI();
    
    
    /**
     * Return the current WorkerThread ID associated with this instance.
     */
    public long getWorkerThreadID();
    
    
    /**
     * Return <tt>true</tt> if the connection header was keep-alive.
     */
    public boolean isKeepAlive();
    
    
    /**
     * Has an error occured duing the HTTP parsing?
     */
    public boolean isError();
}
