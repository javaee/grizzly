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

import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

import com.sun.grizzly.http.webxml.schema.Servlet;

/**
 * Test {@link ServletLoadOnStartupComparator}  folowing these rules
 * 
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
 * 
 * @author Sebastien Dionne
 *
 */
public class ServletLoadOnStartupComparatorTest {
	
	@Test
	public void sortServletTest() throws Exception {
		
		Servlet array[] = new Servlet[7];
		
		// init array
		for(int i=0;i<array.length;i++){
			Servlet servlet = new Servlet();
			servlet.setServletName("servlet"+i);
			
			array[i] = servlet;
		}
		
		// set the values of load-on-startup
		
		array[0].setLoadOnStartup(null);
		array[1].setLoadOnStartup("1");
		array[2].setLoadOnStartup("-1");
		array[3].setLoadOnStartup("3");
		array[4].setLoadOnStartup("0");
		array[5].setLoadOnStartup("5");
		array[6].setLoadOnStartup("6");
		
		Arrays.sort(array, new ServletLoadOnStartupComparator());
		
		// only check the servlet that had a value >=0
		Assert.assertEquals(array[0].getServletName(), "servlet4");
		Assert.assertEquals(array[1].getServletName(), "servlet1");
		Assert.assertEquals(array[2].getServletName(), "servlet3");
		Assert.assertEquals(array[3].getServletName(), "servlet5");
		Assert.assertEquals(array[4].getServletName(), "servlet6");
		
		
		
		
	}
	
}
