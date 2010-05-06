package com.sun.grizzly.http.servlet.deployer;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import junit.framework.TestCase;

import com.sun.grizzly.http.servlet.deployer.conf.DeployableConfiguration;
import com.sun.grizzly.http.servlet.deployer.conf.DeployerServerConfiguration;

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
	
	public void testDeploy() throws Exception {
		
		DeployerServerConfiguration conf = new DeployerServerConfiguration();
	    
	    //conf.locations = "C:/workspaces/workspace_grizzly/modules/bundles/http-servlet-deployer/src/test/resources/HelloServlet.war";
	    conf.cometEnabled = false;
	    
	    deployer.launch(conf);
	    
	    Thread.sleep(2000);
	    
	    System.out.println("Deploying HelloServlet.war");
	    
	    DeployableConfiguration warConf = new DeployableConfiguration("C:/workspaces/workspace_grizzly/modules/bundles/http-servlet-deployer/src/test/resources/HelloServlet.war");
	    
	    deployer.deployApplication(warConf);
	    
	    Thread.sleep(2000);
	    
	    System.out.println("calling the servlet");
	    
	    URL url = new URL("http://localhost:8080/HelloServlet/hello");
	    
	    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	    
	    InputStream is = conn.getInputStream();
	    
	    if(is!=null){
	    	byte[] buffer = new byte[4096];
            int count=0;
            while(is.available()>0) {
            	count = is.read(buffer);
            }
            
            System.out.println("["+new String(buffer,0,count)+"]");
	    }
	    
	    conn.disconnect();
	    
	    System.out.println("Now undeploy HelloServlet");
	    
	    deployer.undeployApplication("/HelloServlet");
	    
	    Thread.sleep(2000);
	    
	    System.out.println("calling the servlet");
	    
	    url = new URL("http://localhost:8080/HelloServlet/hello");
	    
	    conn = (HttpURLConnection) url.openConnection();
	    
	    System.out.println(conn.getResponseCode());
	    
	    assertFalse(conn.getResponseCode()!=404);
	    
	    Thread.sleep(30000);
		
	}
	
}
