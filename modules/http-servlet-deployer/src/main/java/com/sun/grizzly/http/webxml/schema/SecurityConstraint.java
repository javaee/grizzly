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

public class SecurityConstraint {
    public List<String> displayName;  
    public List<WebResourceCollection> webResourceCollection;
    public AuthConstraint authConstraint;
    public UserDataConstraint userDataConstraint;
    
	public List<String> getDisplayName() {
		return displayName;
	}
	public void setDisplayName(List<String> displayName) {
		this.displayName = displayName;
	}
	public List<WebResourceCollection> getWebResourceCollection() {
		return webResourceCollection;
	}
	public void setWebResourceCollection(List<WebResourceCollection> webResourceCollection) {
		this.webResourceCollection = webResourceCollection;
	}
	public AuthConstraint getAuthConstraint() {
		return authConstraint;
	}
	public void setAuthConstraint(AuthConstraint authConstraint) {
		this.authConstraint = authConstraint;
	}
	public UserDataConstraint getUserDataConstraint() {
		return userDataConstraint;
	}
	public void setUserDataConstraint(UserDataConstraint userDataConstraint) {
		this.userDataConstraint = userDataConstraint;
	}
	
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<SecurityConstraint>").append("\n");
		if(displayName!=null && displayName.size()>0){
			List<String> list = displayName;
			
			for (String item : list) {
				buffer.append("<displayName>").append(item).append("</displayName>").append("\n");
			}
		} 
		
		if(userDataConstraint!=null){
			buffer.append(userDataConstraint).append("\n");
		}
		
		if(authConstraint!=null){
			buffer.append(authConstraint).append("\n");
		}

		if(displayName!=null && displayName.size()>0){
			List<String> list = displayName;
			
			for (String item : list) {
				buffer.append("<displayName>").append(item).append("</displayName>").append("\n");
			}
		} 
		
		buffer.append("</SecurityConstraint>");
		return buffer.toString();
	}
}
