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

public class ServiceRef {
	public List<String> description;
    public List<String> displayName;  
    public List<Icon> icon;
    public String serviceRefName;
    public String serviceInterface;
    public String serviceRefType;
    public String wsdlFile;
    public String jaxrpcMappingFile;
    public QName serviceQname;
    public List<PortComponentRef> portComponentRef;
    public List<ServiceRefHandler> handler;
    public ServiceRefHandlerChains handlerChains;
    public String mappedName;
    public List<InjectionTarget> injectionTarget;
    
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public List<String> getDisplayName() {
		return displayName;
	}
	public void setDisplayName(List<String> displayName) {
		this.displayName = displayName;
	}
	public List<Icon> getIcon() {
		return icon;
	}
	public void setIcon(List<Icon> icon) {
		this.icon = icon;
	}
	public String getServiceRefName() {
		return serviceRefName;
	}
	public void setServiceRefName(String serviceRefName) {
		this.serviceRefName = serviceRefName;
	}
	public String getServiceInterface() {
		return serviceInterface;
	}
	public void setServiceInterface(String serviceInterface) {
		this.serviceInterface = serviceInterface;
	}
	public String getServiceRefType() {
		return serviceRefType;
	}
	public void setServiceRefType(String serviceRefType) {
		this.serviceRefType = serviceRefType;
	}
	public String getWsdlFile() {
		return wsdlFile;
	}
	public void setWsdlFile(String wsdlFile) {
		this.wsdlFile = wsdlFile;
	}
	public String getJaxrpcMappingFile() {
		return jaxrpcMappingFile;
	}
	public void setJaxrpcMappingFile(String jaxrpcMappingFile) {
		this.jaxrpcMappingFile = jaxrpcMappingFile;
	}
	public QName getServiceQname() {
		return serviceQname;
	}
	public void setServiceQname(QName serviceQname) {
		this.serviceQname = serviceQname;
	}
	public List<PortComponentRef> getPortComponentRef() {
		return portComponentRef;
	}
	public void setPortComponentRef(List<PortComponentRef> portComponentRef) {
		this.portComponentRef = portComponentRef;
	}
	public List<ServiceRefHandler> getHandler() {
		return handler;
	}
	public void setHandler(List<ServiceRefHandler> handler) {
		this.handler = handler;
	}
	public ServiceRefHandlerChains getHandlerChains() {
		return handlerChains;
	}
	public void setHandlerChains(ServiceRefHandlerChains handlerChains) {
		this.handlerChains = handlerChains;
	}
	public String getMappedName() {
		return mappedName;
	}
	public void setMappedName(String mappedName) {
		this.mappedName = mappedName;
	}
	public List<InjectionTarget> getInjectionTarget() {
		return injectionTarget;
	}
	public void setInjectionTarget(List<InjectionTarget> injectionTarget) {
		this.injectionTarget = injectionTarget;
	}
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<ServiceRef>").append("\n");
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
		
		if(handler!=null && handler.size()>0){
			List<ServiceRefHandler> list = handler;
			
			for (ServiceRefHandler item : list) {
				buffer.append(item).append("\n");
			}
		} 
		
		buffer.append("<handlerChains>").append(handlerChains).append("</handlerChains>").append("\n");
		if(icon!=null && icon.size()>0){
			List<Icon> list = icon;
			
			for (Icon item : list) {
				buffer.append(item).append("\n");
			}
		} 
		buffer.append("<mappedName>").append(mappedName).append("</mappedName>").append("\n");
		
		if(injectionTarget!=null && injectionTarget.size()>0){
			List<InjectionTarget> list = injectionTarget;
			
			for (InjectionTarget item : list) {
				buffer.append(item).append("\n");
			}
		} 
		
		buffer.append("<jaxrpcMappingFile>").append(jaxrpcMappingFile).append("</jaxrpcMappingFile>").append("\n");
		
		if(portComponentRef!=null && portComponentRef.size()>0){
			List<PortComponentRef> list = portComponentRef;
			
			for (PortComponentRef item : list) {
				buffer.append(item).append("\n");
			}
		} 
		
		buffer.append("<serviceInterface>").append(serviceInterface).append("</serviceInterface>").append("\n");
		buffer.append("<serviceQname>").append(serviceQname).append("</serviceQname>").append("\n");
		buffer.append("<serviceRefName>").append(serviceRefName).append("</serviceRefName>").append("\n");
		buffer.append("<serviceRefType>").append(serviceRefType).append("</serviceRefType>").append("\n");
		buffer.append("<wsdlFile>").append(wsdlFile).append("</wsdlFile>").append("\n");
		buffer.append("</ServiceRef>");
		return buffer.toString();
	}
    
}
