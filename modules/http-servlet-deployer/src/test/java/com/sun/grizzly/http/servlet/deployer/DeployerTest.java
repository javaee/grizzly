package com.sun.grizzly.http.servlet.deployer;

import junit.framework.TestCase;

public class DeployerTest extends TestCase {
	
	protected GrizzlyWebServerDeployer deployer = null;
	
	protected void setUp() throws Exception {
		deployer = new GrizzlyWebServerDeployer();
	}

	/**
	 * Extract the servlet path from a URL
	 * 
	 * @throws Exception
	 */
	public void testGetServletPath() throws Exception {
		
		// no context and no servlet
		assertEquals("/", deployer.getServletPath(""));
		
		// no context and root servlet
		assertEquals("/", deployer.getServletPath("/"));
		
		// no context and root servlet with wildcard mapping
		assertEquals("/", deployer.getServletPath("/*"));
		
		// with context
		assertEquals("/servlet1", deployer.getServletPath("/servlet1/"));
		
		// with context and urlPattern
		assertEquals("/servlet1", deployer.getServletPath("/servlet1/*.jsp"));
		
		// with context and urlPattern
		assertEquals("/servlet1", deployer.getServletPath("/servlet1/a.jsp"));
		
		// with context and urlPattern
		assertEquals("/servlet1", deployer.getServletPath("/servlet1/.jsp"));
		
		// with context and urlPattern
		assertEquals("/servlet1", deployer.getServletPath("/servlet1/*"));
		
		//with context and multiple paths
		assertEquals("/servlet1/subpath", deployer.getServletPath("/servlet1/subpath/"));
		
		//with context and multiple paths
		assertEquals("/servlet1/subpath", deployer.getServletPath("/servlet1/subpath"));
		
		//with context and multiple paths and wildcard
		assertEquals("/servlet1/subpath", deployer.getServletPath("/servlet1/subpath/*"));
		
		//with context and multiple paths and wildcard
		assertEquals("/servlet1/subpath/*/abc", deployer.getServletPath("/servlet1/subpath/*/abc"));
		
		//with context and multiple paths and wildcard
		assertEquals("/servlet1/subpath/*/abc", deployer.getServletPath("/servlet1/subpath/*/abc/1.zxy"));
		
	}
	
	/**
	 * Extract the servlet path from a URL
	 * 
	 * @throws Exception
	 */
	public void testGetContextPath() throws Exception {
		// no context 
		assertEquals("/", deployer.getContext(""));
		
		// root context 
		assertEquals("/", deployer.getContext("/"));
		
		// with context
		assertEquals("/war1", deployer.getContext("/war1/"));
		
		// with context
		assertEquals("/war1", deployer.getContext("war1/"));
		
		// with context
		assertEquals("/war1", deployer.getContext("war1"));
		
	}
	
}
