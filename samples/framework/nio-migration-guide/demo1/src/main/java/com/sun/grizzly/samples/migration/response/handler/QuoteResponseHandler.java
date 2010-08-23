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

package com.sun.grizzly.samples.migration.response.handler;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.sun.grizzly.samples.migration.command.ICommand;
import com.sun.grizzly.samples.migration.command.ICommandRequest;
import com.sun.grizzly.samples.migration.command.QuoteCommand;
import com.sun.grizzly.samples.migration.connection.handler.ClientConnectionHandler;

/**
 * This class handler the response.
 * 
 * The response will be send to the client.
 * 
 * @author Sebastien Dionne
 *
 */
public class QuoteResponseHandler implements IResponseHandler {

	private QuoteCommand f_command;
	private ClientConnectionHandler f_clientConnectionHandler;
	private ICommandRequest f_commandRequest;
	
	
	@Override
	public ClientConnectionHandler getClientConnectionHandler() {
		return f_clientConnectionHandler;
	}

	@Override
	public ICommand getCommand() {
		return f_command;
	}

	@Override
	public ICommandRequest getCommandRequest() {
		return f_commandRequest;
	}

	@Override
	public void sendLastUpdateToClient(ClientConnectionHandler clientConnectionHandler) {
		// not implemented in this demo
	}

	@Override
	public void sendToClient(StringBuffer sb) {
		
		ByteBuffer writeBuffer = ByteBuffer.allocateDirect(sb.toString().getBytes().length);
		
		writeBuffer.put(sb.toString().getBytes());
		
		writeBuffer.flip();
		
		System.out.println("SENDING QUOTE TO CLIENT =" + sb.toString());
		
		try {
			getClientConnectionHandler().getSocketChannel().write(writeBuffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void setClientConnectionHandler(ClientConnectionHandler clientConnectionHandler) {
		f_clientConnectionHandler = clientConnectionHandler;
	}

	@Override
	public void setCommand(ICommand command) {
		f_command = (QuoteCommand)command;
	}

	@Override
	public void setCommandRequest(ICommandRequest commandRequest) {
		f_commandRequest = commandRequest;
	}

}
