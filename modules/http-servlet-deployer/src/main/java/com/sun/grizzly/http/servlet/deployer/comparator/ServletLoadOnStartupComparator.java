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
