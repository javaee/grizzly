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

import javax.xml.namespace.QName;

public class ServiceRefHandler {
	public List<Icon> icon;
    public List<String> displayName;  
    public List<String> description;
    public String handlerName;
    public String handlerClass;
    public List<InitParam> initParam;
    public List<QName> soapHeader;
    public List<String> soapRole;
    public List<String> portName;
    
	public List<Icon> getIcon() {
		return icon;
	}
	public void setIcon(List<Icon> icon) {
		this.icon = icon;
	}
	public List<String> getDisplayName() {
		return displayName;
	}
	public void setDisplayName(List<String> displayName) {
		this.displayName = displayName;
	}
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public String getHandlerName() {
		return handlerName;
	}
	public void setHandlerName(String handlerName) {
		this.handlerName = handlerName;
	}
	public String getHandlerClass() {
		return handlerClass;
	}
	public void setHandlerClass(String handlerClass) {
		this.handlerClass = handlerClass;
	}
	public List<InitParam> getInitParam() {
		return initParam;
	}
	public void setInitParam(List<InitParam> initParam) {
		this.initParam = initParam;
	}
	public List<QName> getSoapHeader() {
		return soapHeader;
	}
	public void setSoapHeader(List<QName> soapHeader) {
		this.soapHeader = soapHeader;
	}
	public List<String> getSoapRole() {
		return soapRole;
	}
	public void setSoapRole(List<String> soapRole) {
		this.soapRole = soapRole;
	}
	public List<String> getPortName() {
		return portName;
	}
	public void setPortName(List<String> portName) {
		this.portName = portName;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<ServiceRefHandler>").append("\n");
		if(description!=null && description.size()>0){
			List<String> list = description;
			
			for (String item : list) {
				buffer.append("<description>").append(item).append("</description>").append("\n");
			}
		} 
		if(displayName!=null && displayName.size()>0){
			List<String> list = displayName;
			
			for (String item : list) {
				buffer.append("<displayName>").append(item).append("</displayName>").append("\n");
			}
		} 
		buffer.append("<handlerClass>").append(handlerClass).append("</handlerClass>").append("\n");
		buffer.append("<handlerName>").append(handlerName).append("</handlerName>").append("\n");
		if(icon!=null && icon.size()>0){
			List<Icon> list = icon;
			
			for (Icon item : list) {
				buffer.append(item).append("\n");
			}
		} 
		buffer.append("<initParam>").append(initParam).append("</initParam>").append("\n");
		buffer.append("<portName>").append(portName).append("</portName>").append("\n");
		
		if(soapHeader!=null && soapHeader.size()>0){
			List<QName> list = soapHeader;
			
			for (QName item : list) {
				buffer.append(item).append("\n");
			}
		} 
		
		buffer.append("<soapRole>").append(soapRole).append("</soapRole>").append("\n");
		buffer.append("</ServiceRefHandler>");
		return buffer.toString();
	}
    
    
}
