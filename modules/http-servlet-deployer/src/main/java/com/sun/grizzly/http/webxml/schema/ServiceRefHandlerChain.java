/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class ServiceRefHandlerChain {
	public String serviceNamePattern;
    public String portNamePattern;
    public List<String> protocolBindings;
    public List<ServiceRefHandler> handler;
    
	public String getServiceNamePattern() {
		return serviceNamePattern;
	}
	public void setServiceNamePattern(String serviceNamePattern) {
		this.serviceNamePattern = serviceNamePattern;
	}
	public String getPortNamePattern() {
		return portNamePattern;
	}
	public void setPortNamePattern(String portNamePattern) {
		this.portNamePattern = portNamePattern;
	}
	public List<String> getProtocolBindings() {
		return protocolBindings;
	}
	public void setProtocolBindings(List<String> protocolBindings) {
		this.protocolBindings = protocolBindings;
	}
	public List<ServiceRefHandler> getHandler() {
		return handler;
	}
	public void setHandler(List<ServiceRefHandler> handler) {
		this.handler = handler;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<ServiceRefHandlerChain>").append("\n");
		buffer.append("<portNamePattern>").append(portNamePattern).append("</portNamePattern>").append("\n");
		
		if(handler!=null && handler.size()>0){
			List<ServiceRefHandler> list = handler;
			
			for (ServiceRefHandler item : list) {
				buffer.append(item).append("\n");
			}
		} 
		
		if(protocolBindings!=null && protocolBindings.size()>0){
			buffer.append("<protocolBindings>").append("\n");
			
			List<String> list = protocolBindings;
			
			for (String item : list) {
				buffer.append(item).append("\n");
			}
			buffer.append("</protocolBindings>").append("\n");
		} 
		
		buffer.append("<serviceNamePattern>").append(serviceNamePattern).append("</serviceNamePattern>").append("\n");
		buffer.append("</ServiceRefHandlerChain>");
		return buffer.toString();
	}
    
}
