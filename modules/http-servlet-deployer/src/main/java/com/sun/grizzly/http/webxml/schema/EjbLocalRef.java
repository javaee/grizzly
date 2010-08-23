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

public class EjbLocalRef {
	public List<String> description;  
    public String ejbRefName;
    public String ejbRefType;
    public String localHome;
    public String local;
    public String ejbLink;
    public String mappedName;
    public List<InjectionTarget> injectionTarget;
    
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public String getEjbRefName() {
		return ejbRefName;
	}
	public void setEjbRefName(String ejbRefName) {
		this.ejbRefName = ejbRefName;
	}
	public String getEjbRefType() {
		return ejbRefType;
	}
	public void setEjbRefType(String ejbRefType) {
		this.ejbRefType = ejbRefType;
	}
	public String getLocalHome() {
		return localHome;
	}
	public void setLocalHome(String localHome) {
		this.localHome = localHome;
	}
	public String getLocal() {
		return local;
	}
	public void setLocal(String local) {
		this.local = local;
	}
	public String getEjbLink() {
		return ejbLink;
	}
	public void setEjbLink(String ejbLink) {
		this.ejbLink = ejbLink;
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
		buffer.append("<EjbLocalRef>").append("\n");
		if(description!=null && description.size()>0){
			List<String> list = description;
			
			for (String item : list) {
				buffer.append("<description>").append(item).append("</description>").append("\n");
			}
		} 
		buffer.append("<mappedName>").append(mappedName).append("</mappedName>").append("\n");
		
		if(injectionTarget!=null && injectionTarget.size()>0){
			List<InjectionTarget> list = injectionTarget;
			
			for (InjectionTarget item : list) {
				buffer.append(item).append("\n");
			}
		} 
		buffer.append("<ejbLink>").append(ejbLink).append("</ejbLink>").append("\n");
		buffer.append("<ejbRefName>").append(ejbRefName).append("</ejbRefName>").append("\n");
		buffer.append("<ejbRefType>").append(ejbRefType).append("</ejbRefType>").append("\n");
		buffer.append("<local>").append(local).append("</local>").append("\n");
		buffer.append("<localHome>").append(localHome).append("</localHome>").append("\n");
		buffer.append("</EjbLocalRef>");
		return buffer.toString();
	}
}
