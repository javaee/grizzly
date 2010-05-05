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
