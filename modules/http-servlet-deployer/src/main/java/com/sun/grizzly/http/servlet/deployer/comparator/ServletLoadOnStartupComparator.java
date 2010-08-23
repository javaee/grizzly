/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.servlet.deployer.comparator;

import java.util.Comparator;
import com.sun.grizzly.http.webxml.schema.Servlet;

public class ServletLoadOnStartupComparator implements Comparator<Servlet> {

	/**
	 * The load-on-startup element indicates that this servlet should be
	 * loaded (instantiated and have its init() called) on the startup of the
	 * web application. The optional contents of these element must be an
	 * integer indicating the order in which the servlet should be loaded. If
	 * the value is a negative integer, or the element is not present, the
	 * container is free to load the servlet whenever it chooses. If the value
	 * is a positive integer or 0, the container must load and initialize the
	 * servlet as the application is deployed. The container must guarantee that
	 * servlets marked with lower integers are loaded before servlets marked
	 * with higher integers. The container may choose the order of loading of
	 * servlets with the same load-on-start-up value.
	 */
	public int compare(Servlet o1, Servlet o2) {
		
		if(o1.loadOnStartup==null || o1.loadOnStartup.trim().length()==0){
			return 1;
		}
		
		if(o1.loadOnStartup==o2.loadOnStartup){
			return 0;
		}
		
		int loadOnStartup1 = -1;
		int loadOnStartup2 = -1;
		
		try {
			loadOnStartup1 = Integer.parseInt(o1.loadOnStartup);
		} catch (NumberFormatException e) {
		}
		
		try {
			loadOnStartup2 = Integer.parseInt(o2.loadOnStartup);
		} catch (NumberFormatException e) {
		}
		
		// le plus petit doit etre en 1er dans la liste sauf le -1
		if(loadOnStartup1==-1){
			return 1;
		} else if(loadOnStartup2==-1){
			return -1;
		}else if(loadOnStartup1 < loadOnStartup2){
			return -1;
		} else if(loadOnStartup1 == loadOnStartup2){
			return 0;
		} else if(loadOnStartup1 > loadOnStartup2){
			return 1;
		} 
		
		return 1;
	}

}
