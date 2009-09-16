package com.sun.grizzly.http.servlet.deployer;

import junit.framework.TestCase;

public class DeployerTest extends TestCase {
	
	private GrizzlyWebServerDeployer deployer = null;

	protected void setUp() throws Exception {
		deployer = new GrizzlyWebServerDeployer();
	}

	/**
	 * Extract the servlet path from a URL
	 * 
	 * @throws Exception Duh!
	 */
	public void testGetServletPath() throws Exception {
		
		// no context and no servlet
		assertEquals("/", WebAppAdapter.getServletPath(""));
		
		// no context and root servlet
		assertEquals("/", WebAppAdapter.getServletPath("/"));
		
		// no context and root servlet with wildcard mapping
		assertEquals("/", WebAppAdapter.getServletPath("/*"));
		
		// with context
		assertEquals("/servlet1", WebAppAdapter.getServletPath("/servlet1/"));
		
		// with context and urlPattern
		assertEquals("/servlet1", WebAppAdapter.getServletPath("/servlet1/*.jsp"));
		
		// with context and urlPattern
		assertEquals("/servlet1", WebAppAdapter.getServletPath("/servlet1/a.jsp"));
		
		// with context and urlPattern
		assertEquals("/servlet1", WebAppAdapter.getServletPath("/servlet1/.jsp"));
		
		// with context and urlPattern
		assertEquals("/servlet1", WebAppAdapter.getServletPath("/servlet1/*"));
		
		//with context and multiple paths
		assertEquals("/servlet1/subpath", WebAppAdapter.getServletPath("/servlet1/subpath/"));
		
		//with context and multiple paths
		assertEquals("/servlet1/subpath", WebAppAdapter.getServletPath("/servlet1/subpath"));
		
		//with context and multiple paths and wildcard
		assertEquals("/servlet1/subpath", WebAppAdapter.getServletPath("/servlet1/subpath/*"));
		
		//with context and multiple paths and wildcard
		assertEquals("/servlet1/subpath/*/abc", WebAppAdapter.getServletPath("/servlet1/subpath/*/abc"));
		
		//with context and multiple paths and wildcard
		assertEquals("/servlet1/subpath/*/abc", WebAppAdapter.getServletPath("/servlet1/subpath/*/abc/1.zxy"));
		
	}
	
	/**
	 * Extract the servlet path from a URL
	 * 
	 * @throws Exception Duh!
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
