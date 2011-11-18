/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.servlet.deployer.annotation;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.annotation.Resources;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RunAs;

import com.sun.grizzly.http.webxml.schema.EnvEntry;
import com.sun.grizzly.http.webxml.schema.LifecycleCallback;
import com.sun.grizzly.http.webxml.schema.MessageDestinationRef;
import com.sun.grizzly.http.webxml.schema.ResourceRef;
import com.sun.grizzly.http.webxml.schema.SecurityRole;
import com.sun.grizzly.http.webxml.schema.ServiceRef;
import com.sun.grizzly.http.webxml.schema.Servlet;
import com.sun.grizzly.http.webxml.schema.WebApp;

public class AnnotationParser {
	private static Logger logger = Logger.getLogger(AnnotationParser.class.getName());
	
	/*
	 * This annotation is used to define the security roles that comprise the
	 * security model of the application. This annotation is specified on a
	 * class, and it typically would be used to define roles that could be
	 * tested (i.e., by calling isUserInRole) from within the methods of the
	 * annotated class. It could also be used to declare application roles that
	 * are not implicitly declared as the result of their use in a
	 * 
	 * @DeclareRoles annotation on the class implementing the javax.servlet.
	 * Servlet interface or a subclass thereof. Following is an example of how
	 * this annotation would be used.
	 * 
	 * @DeclareRoles("BusinessAdmin") public class CalculatorServlet { //... }
	 * Declaring @DeclaresRoles ("BusinessAdmin") is equivalent to defining the
	 * following in the web.xml. 
	 * 
	 * <web-app> 
	 *    <security-role>
	 *       <role-name>BusinessAdmin</role-name> 
	 *    </security-role> 
	 * </web-app>
	 * 
	 * JSR-250
	 */
	protected void parseDeclareRoles(WebApp webapp, Class<?> clazz) throws Exception {

		if (webapp == null || clazz == null) {
			return;
		}

		DeclareRoles an = (DeclareRoles) clazz.getAnnotation(DeclareRoles.class);

		if (an != null) {
			logger.fine("Annotation found : " + an.toString());

			for (String value : an.value()) {
				if(value!=null){
					SecurityRole role = new SecurityRole();
					role.setRoleName(value);
	
					webapp.getSecurityRole().add(role);
				}
			}

		} 

	}

	/*
	 * Enterprise JavaBeansTM 3.0 (EJB) components may referenced from a web
	 * component using the @EJB annotation. The @EJB annotation provides the
	 * equivalent functionality of declaring the ejb-ref or ejb-local-ref
	 * elements in the deployment descriptor. Fields that have a corresponding
	 * 
	 * @EJB annotation are injected with the a reference to the corresponding
	 * EJB component.
	 * 
	 * An example:
	 * 
	 * @EJB private ShoppingCart myCart;
	 * 
	 * In the case above a reference to the EJB component “myCart” is injected
	 * as the value of the private field “myCart” prior to the classs declaring
	 * the injection being made available. The behavior the @EJB annotation is
	 * further detailed in section 15.5 of the EJB 3.0 specification (JSR220).
	 */
	protected void parseEJB(WebApp webapp, Class<?> clazz) throws Exception {
		if (webapp == null || clazz == null) {
			return;
		}
		
		logger.finest("Not Implemented");
	}

	/*
	 * The @EJBs annotation allows more than one @EJB annotations to be declared
	 * on a single resource. An example:
	 * 
	 * @EJBs({@EJB(Calculator), @EJB(ShoppingCart)}) public class
	 * ShoppingCartServlet { //... } The example above the EJB components
	 * ShoppingCart and Calculator are made available to ShoppingCartServlet.
	 * The ShoppingCartServlet must still look up the references using JNDI but
	 * the EJBs do not need to declared in the web.xml file
	 * 
	 * The @EJBs annotation is discussed in further detailed in section 15.5 of
	 * the EJB 3.0 specification (JSR220).
	 */
	protected void parseEJBs(WebApp webapp, Class<?> clazz) throws Exception {
		if (webapp == null || clazz == null) {
			return;
		}

		logger.finest("Not Implemented");
	}

	/*
	 * The @Resource annotation is used to declare a reference to a resource
	 * such as a data source, Java Messaging Service (JMS) destination, or
	 * environment entry. This annotation is equivalent to declaring a
	 * resource-ref, message-destinationref or env-ref, or resource-env-ref
	 * element in the deployment descriptor. The @Resource annotation is
	 * specified on a class, method or field. The container is responsible
	 * injecting references to resources declared by the
	 * 
	 * @Resource annotation and mapping it to the proper JNDI resources. See the
	 * Java EE Specification Chapter 5 for further details. An example of a
	 * 
	 * @Resource annotation follows:
	 * 
	 * @Resource private javax.sql.DataSource catalogDS; public
	 * getProductsByCategory() { // get a connection and execute the query
	 * Connection conn = catalogDS.getConnection(); .. }
	 * 
	 * JSR-250
	 */
	protected void parseResource(WebApp webapp, Class<?> clazz) throws Exception {
		if (webapp == null || clazz == null) {
			return;
		}
		
		Resource an = (Resource)clazz.getAnnotation(Resource.class);
		
		if(an!=null){
			logger.fine("Annotation found in Class Level: " + an.toString());
			populateResource(webapp, an);
		}
		
		for (Field field : clazz.getFields()) {
			Resource resource = (Resource) field.getAnnotation(Resource.class);
			
			if (resource != null) {
				logger.fine("Annotation found in Field Level: " + field.toString() + "  Resource found : " + resource.toString());
				populateResource(webapp, resource);
				
			}
		}
		
		for (Method method : clazz.getMethods()) {
			Resource resource = (Resource) method.getAnnotation(Resource.class);
			
			if (resource != null) {
				logger.fine("Annotation found in Method Level: " + method.toString() + "  Resource found : " + resource.toString());
				populateResource(webapp, resource);
			}
		}
		
	}

	/*
	 * The PersistenceContexts annotation allows more than one
	 * 
	 * @PersistenceContext to be declared on a resource. The behavior the
	 * 
	 * @PersistenceContext annotation is further detailed in section 8.4.1 of
	 * the Java Persistence document which is part of the EJB 3.0 specification
	 * (JSR220) and in section 15.11 of the EJB 3.0 specification
	 * 
	 * @PersistenceContext (type=EXTENDED) EntityManager em;
	 */
	protected void parsePersistenceContext(WebApp webapp, Class<?> clazz) throws Exception {
		if (webapp == null || clazz == null) {
			return;
		}

		logger.finest("Not Implemented");
	}

	/*
	 * The @PersistenceUnit annotation provides Enterprise Java Beans components
	 * declared in a servlet a reference to a entity manager factory. The entity
	 * manager factory is bound to a separate persistence.xml configuration file
	 * as described in section 5.10 of the EJB 3.0 specification (JSR220). An
	 * example:
	 * 
	 * @PersistenceUnit EntityManagerFactory emf; The behavior the
	 * 
	 * @PersistenceUnit annotation is further detailed in section 8.4.2 of the
	 * Java Persistence document which is part of the EJB 3.0 specification
	 * (JSR220) and in section 15.10 of the EJB 3.0 specification.
	 */
	protected void parsePersistenceUnit(WebApp webapp, Class<?> clazz) throws Exception {
		if (webapp == null || clazz == null) {
			return;
		}

		logger.finest("Not Implemented");
	}

	/*
	 * This annotation allows for more than one @PersistentUnit annotations to
	 * be declared on a resource. The behavior the @PersistenceUnits annotation
	 * is further detailed in section 8.4.2 of the Java Persistence document
	 * which is part of the EJB 3.0 specification (JSR220) and in section 15.10
	 * of the EJB 3.0 specification..
	 */
	protected void parsePersistenceUnits(WebApp webapp, Class<?> clazz) throws Exception {
		if (webapp == null || clazz == null) {
			return;
		}

		logger.finest("Not Implemented");
	}

	/*
	 * The @PostConstruct annotation is declared on a method that does not take
	 * any arguments, and must not throw any checked expections. The return
	 * value must be void. The method MUST be called after the resources
	 * injections have been completed and before any lifecycle methods on the
	 * component are called. An example:
	 * 
	 * @PostConstruct public void postConstruct() { ... } The example above
	 * shows a method using the @PostConstruct annotation.
	 */
	protected void parsePostConstruct(WebApp webapp, Class<?> clazz) throws Exception {
		if (webapp == null || clazz == null) {
			return;
		}

		for (Method method : clazz.getMethods()) {
			PostConstruct an = (PostConstruct) method.getAnnotation(PostConstruct.class);
			
			if (an != null) {
				logger.fine("Annotation found in Method Level: " + method.toString() + "  Resource found : " + an.toString());
				
				LifecycleCallback callback = new LifecycleCallback();
				callback.setLifecycleCallbackClass(clazz.getName());
				callback.setLifecycleCallbackMethod(method.getName());
				
				webapp.getPostConstruct().add(callback);
			}
		}
		
	}

	/*
	 * The @PreDestroy annotation is declared on a method of a container managed
	 * component. The method is called prior to component being reomvoed by the
	 * container. An example:
	 * 
	 * @PreDestroy public void cleanup() { // clean up any open resources ... }
	 * The method annotated with @PreDestroy must return void and must not throw
	 * a checked exception. The method may be public, protected, package private
	 * or private. The method must not be static however it may be final.
	 * 
	 * JSR-250
	 */
	protected void parsePreDestroy(WebApp webapp, Class<?> clazz) throws Exception {
		if (webapp == null || clazz == null) {
			return;
		}

		for (Method method : clazz.getMethods()) {
			PreDestroy an = (PreDestroy) method.getAnnotation(PreDestroy.class);
			
			if (an != null) {
				logger.fine("Annotation found in Method Level: " + method.toString() + "  Resource found : " + an.toString());
				
				LifecycleCallback callback = new LifecycleCallback();
				callback.setLifecycleCallbackClass(clazz.getName());
				callback.setLifecycleCallbackMethod(method.getName());
				
				webapp.getPreDestroy().add(callback);
			}
		}
	}
	
	private static void populateResource(WebApp webapp, Resource resource) throws Exception {
		if (webapp == null || resource == null) {
			return;
		}
		
		if(resource.type().getCanonicalName().endsWith("Service")){
			
			logger.finest("Populate Resource as : ServiceRef");
			
			// service-ref element
            ServiceRef serviceRef = new ServiceRef();
            
            serviceRef.setServiceRefName(resource.name());
            serviceRef.setServiceRefType(resource.type().getName());
            serviceRef.setMappedName(resource.mappedName());
            
            List<String> description = new ArrayList<String>();
            description.add(resource.description());
            serviceRef.setDescription(description);
            
            webapp.getServiceRef().add(serviceRef);
            
		} else if (resource.type().getCanonicalName().equals("javax.sql.DataSource") ||
				resource.type().getCanonicalName().endsWith("ConnectionFactory") ||
                resource.type().getCanonicalName().equals("javax.mail.Session") ||
                resource.type().getCanonicalName().equals("java.net.URL")){
			
			logger.finest("Populate Resource as : ResourceRef");
			
			// ?????
			
			// resource-ref element  or resource-env-ref element
			
			// resource-ref element
			
			ResourceRef resourceRef = new ResourceRef();
			
			List<String> description = new ArrayList<String>();
            description.add(resource.description());
            resourceRef.setDescription(description);
            
            resourceRef.setMappedName(resource.mappedName());
           
            if (resource.authenticationType() == Resource.AuthenticationType.CONTAINER) {
            	resourceRef.setResAuth("Container");
            }
            else if (resource.authenticationType() == Resource.AuthenticationType.APPLICATION) {
            	resourceRef.setResAuth("Application");
            }
            
            resourceRef.setResRefName(resource.name());
            resourceRef.setResSharingScope(resource.shareable() ? "Shareable" : "Unshareable");
            resourceRef.setResType(resource.type().getName());
			
			webapp.getResourceRef().add(resourceRef);
			
		} else if (resource.type().getCanonicalName().equals("javax.jms.Queue") ||
				resource.type().getCanonicalName().equals("javax.jms.Topic")) {
            
			logger.finest("Populate Resource as : MessageDestinationRef");
			
            // message-destination-ref
            MessageDestinationRef messageRef = new MessageDestinationRef();
            
            List<String> description = new ArrayList<String>();
            description.add(resource.description());
            messageRef.setDescription(description);
            
            messageRef.setMappedName(resource.mappedName());
            messageRef.setMessageDestinationRefName(resource.name());
            messageRef.setMessageDestinationType(resource.type().getName());
            messageRef.setMessageDestinationUsage(resource.mappedName());
            
            webapp.getMessageDestinationRef().add(messageRef);
            
        } else if (resource.type().getCanonicalName().equals("java.lang.String") ||
        		resource.type().getCanonicalName().equals("java.lang.Character") ||
        		resource.type().getCanonicalName().equals("java.lang.Integer") ||
        		resource.type().getCanonicalName().equals("java.lang.Boolean") ||
        		resource.type().getCanonicalName().equals("java.lang.Double") ||
        		resource.type().getCanonicalName().equals("java.lang.Byte") ||
        		resource.type().getCanonicalName().equals("java.lang.Short") ||
        		resource.type().getCanonicalName().equals("java.lang.Long") ||
        		resource.type().getCanonicalName().equals("java.lang.Float")) {
            
        	logger.finest("Populate Resource as : EnvEntry");
        	
            // env-entry element
            
            EnvEntry entry = new EnvEntry();
            
            entry.setEnvEntryName(resource.name());
            entry.setEnvEntryValue(resource.mappedName());
            entry.setEnvEntryType(resource.type().getName());
            entry.setMappedName(resource.mappedName());
            
            List<String> description = new ArrayList<String>();
            description.add(resource.description());
            entry.setDescription(description);
            
            webapp.getEnvEntry().add(entry);
            
        } 
		
	}

	/*
	 * The @Resources annotation acts as a container for multiple @Resource
	 * annotations because the Java MetaData specification does not allow for
	 * multiple annotations with the same name on the same annotation target. An
	 * example:
	 * 
	 * @Resources ({
	 * 
	 * @Resource(name=”myDB” type=javax.sql.DataSource),
	 * 
	 * @Resource(name=”myMQ” type=javax.jms.ConnectionFactory) }) public class
	 * CalculatorServlet { //... } In the example above a JMS connection factory
	 * and a data source are made available to the CalculatorServlet by means of
	 * an @Resources annotation. The semantics of the @Resources annotation are
	 * further detailed in the Common Annotations for the JavaTM PlatformTM
	 * specifcation (JSR 250) section 2.4.
	 */
	protected void parseResources(WebApp webapp, Class<?> clazz) throws Exception {
		if (webapp == null || clazz == null) {
			return;
		}
		
		Resources an = (Resources) clazz.getAnnotation(Resources.class);

		if (an != null && an.value()!=null) {
			logger.fine("Annotation found : " + an.toString());
			
			for (Resource resource : an.value()) {
				populateResource(webapp, resource);
			}

		} 
		
		for (Method method : clazz.getMethods()) {
			Resources resources = (Resources) method.getAnnotation(Resources.class);
			
			if (resources != null && resources.value()!=null) {
				for (Resource resource : an.value()) {
					logger.fine("Annotation found in Method Level: " + method.toString() + "  Resource found : " + an.toString());
					populateResource(webapp, resource);
				}
				
			}
		}
		
		for (Field field : clazz.getFields()) {
			Resources resources = (Resources) field.getAnnotation(Resources.class);
			
			if (resources != null && resources.value()!=null) {
				for (Resource resource : an.value()) {
					logger.fine("Annotation found in Field Level: " + field.toString() + "  Resource found : " + an.toString());
					populateResource(webapp, resource);
				}
				
			}
		}
	}

	/*
	 * The @RunAs annotation is equivalent to the run-as element in the
	 * deployment descriptor. The @RunAs annotation may only be defined in
	 * classes implementing the javax.servlet.Servlet interface or a subclass
	 * thereof. An example:
	 * 
	 * @RunAs(“Admin”) public class CalculatorServlet {
	 * 
	 * @EJB private ShoppingCart myCart; public void doGet(HttpServletRequest,
	 * req, HttpServletResponse res) { //.... myCart.getTotal(); //.... } }
	 * //.... } The @RunAs(“Admin”) statement would be equivalent to defining
	 * the following in the web.xml. <servlet>
	 * 
	 * <servlet-name>CalculatorServlet</servlet-name>
	 *    <run-as>Admin</run-as>
	 * </servlet>
	 * 
	 * The example above shows how a servlet uses the @RunAs annotation to
	 * propagate the security identity “Admin” to an EJB component when the
	 * myCart.getTotal() method is called. For further details on propagating
	 * identities see SRV.14.3.1. For further details on the @RunAs annotation
	 * refer to the Common Annotations for the JavaTM PlatformTM specifcation
	 * (JSR 250) section 2.6
	 */
	protected void parseRunAs(WebApp webapp, Class<?> clazz) throws Exception {
		if (webapp == null || clazz == null) {
			return;
		}

		if (javax.servlet.Servlet.class.isAssignableFrom(clazz)) {
			RunAs an = (RunAs) clazz.getAnnotation(RunAs.class);
			
			if (an != null) {
				logger.fine("Annotation found : " + an.toString());
				
				com.sun.grizzly.http.webxml.schema.RunAs runAs = new com.sun.grizzly.http.webxml.schema.RunAs();
				runAs.setRoleName(an.value());
				
				Servlet servlet = new Servlet();
				servlet.setServletClass(clazz.getName());
				servlet.setRunAs(runAs);
				
				webapp.getServlet().add(servlet);

			} 
		} 
		
	}

	/*
	 * The @WebServiceRef annotation provides a reference to a web service in a
	 * web component in same way as a resource-ref element would in the
	 * deployment descriptor. An example:
	 * 
	 * @WebServiceRef private MyService service; 
	 * 
	 * In this example a reference to
	 * the web service “MyService” will be injected to the class declaring the
	 * annotation. This annotation and behavior are further detailed in the
	 * JAX-WS Specification (JSR 224) section 7.
	 */
	protected void parseWebServiceRef(WebApp webapp, Class<?> clazz) throws Exception {
		if (webapp == null || clazz == null) {
			return;
		}
		
		/*
		for (Field field : clazz.getFields()) {
			WebServiceRef an = (WebServiceRef) clazz.getAnnotation(WebServiceRef.class);
			
			if (an != null) {
				System.out.println(an.toString() + " " + field.toString());
				
				ServiceRef serviceRef = new ServiceRef();
				
				serviceRef.setMappedName(an.mappedName());
				serviceRef.setWsdlFile(an.wsdlLocation());
				serviceRef.setServiceRefName(an.name());
				serviceRef.setServiceRefType(an.type().getName());
				
				PortComponentRef portComponentRef = new PortComponentRef();
	            portComponentRef.setPortComponentLink(an.type().getCanonicalName());
	            
	            
				if (an.value() == null){
					serviceRef.setServiceInterface(an.type().getCanonicalName());
				}
	            
	            if (an.type().getCanonicalName().equals("Service")){
	            	serviceRef.setServiceInterface(an.type().getCanonicalName());
	            }
	            
	            if (an.type().getCanonicalName().equals("Endpoint")){
	            	portComponentRef.setServiceEndpointInterface(an.type().getCanonicalName());
	            }
	            
	            List<PortComponentRef> list = new ArrayList<PortComponentRef>();
	            list.add(portComponentRef);
	            
	            serviceRef.setPortComponentRef(list);
				
				webapp.getServiceRef().add(serviceRef);
			}
		}
		*/
		
		logger.finest("Not Implemented");
		
	}

	/*
	 * This annotation allows for more than one @WebServiceRef annotations to be
	 * declared on a single resource. The behavior of this annotation is further
	 * detailed in the JAX-WS Specification (JSR 224) section 7.
	 */
	protected void parseWebServiceRefs(WebApp webapp, Class<?> clazz) throws Exception {
		if (webapp == null || clazz == null) {
			return;
		}
		/*
		WebServiceRefs an = (WebServiceRefs) clazz.getAnnotation(WebServiceRefs.class);

		if (an != null) {
			logger.fine("Annotation found : " + an.toString());
			/

		} else {
			System.out.println("Annotation not found");
		}
		*/
		
		logger.finest("Not Implemented");
	}

	/*
	 * javax.servlet.Servlet # javax.servlet.Filter #
	 * javax.servlet.ServletContextListener #
	 * javax.servlet.ServletContextAttributeListener #
	 * javax.servlet.ServletRequestListener #
	 * javax.servlet.ServletRequestAttributeListener #
	 * javax.servlet.http.HttpSessionListener #
	 * javax.servlet.http.HttpSessionAttributeListener
	 */
	public boolean isValid(Class<?> clazz) throws InstantiationException, IllegalAccessException {
		if (clazz == null) {
			return false;
		}

		if(javax.servlet.Servlet.class.isAssignableFrom(clazz) || 
				javax.servlet.Filter.class.isAssignableFrom(clazz) || 
				javax.servlet.ServletContextListener.class.isAssignableFrom(clazz) || 
				javax.servlet.ServletContextAttributeListener.class.isAssignableFrom(clazz) || 
				javax.servlet.ServletRequestListener.class.isAssignableFrom(clazz) ||
				javax.servlet.ServletRequestAttributeListener.class.isAssignableFrom(clazz) || 
				javax.servlet.http.HttpSessionListener.class.isAssignableFrom(clazz) || 
				javax.servlet.http.HttpSessionAttributeListener.class.isAssignableFrom(clazz)) {
			
				return true;
		}

		return false;

	}
	
	
	protected void parseAnnotation(Class<?> clazz, WebApp webapp) throws Exception {
		
		if (!isValid(clazz)) {
			return;
		}
		
		logger.finest("Searching for Annotations for this class : " + clazz.getName());
		
		parseDeclareRoles(webapp, clazz);
		parseResource(webapp, clazz);
		parseResources(webapp, clazz);
		parsePostConstruct(webapp, clazz);
		parsePreDestroy(webapp, clazz);
		parseRunAs(webapp, clazz);
		parseWebServiceRef(webapp, clazz);
		parseWebServiceRefs(webapp, clazz);
		
		parsePersistenceContext(webapp, clazz);
		parsePersistenceUnit(webapp, clazz);
		parsePersistenceUnits(webapp, clazz);
		parseEJB(webapp, clazz);
		parseEJBs(webapp, clazz);
		
	}
	
	public List<String> listFilesAndFolders(String folder, int tabCounter) {

		
		List<String> list = new ArrayList<String>();
		
	    File file = new File(folder);

	    if (!file.exists() || !file.isDirectory()) {

	      logger.info("Parameter is not a directory : " + folder);
	      return list;

	    }

	    File[] fileArray = file.listFiles(new FileFilter() {
			
			public boolean accept(File pathname) {
				if(pathname.isDirectory() || pathname.getName().endsWith(".class")){
					return true;
				}
				return false;
			}
		});

	    for (int i = 0; i < fileArray.length; i++) {

	      if (fileArray[i].isDirectory()) {
	    	  list.addAll(listFilesAndFolders(fileArray[i].getAbsolutePath(), tabCounter+1));
	    	  
	      } else {
	    	  String toSkip = "WEB-INF" + File.separator + "classes" + File.separator;
	    	  
	    	  String path = fileArray[i].getPath(); 
	    	  path = path.substring(path.indexOf(toSkip)+toSkip.length());
	    	  
	    	  String classname = path.replace('\\', '/').replace('/', '.');
	    	  
	    	  list.add(classname);
	    	  
	      }
	    }

	    return list;
	  }
	
	
	public WebApp parseAnnotation(URLClassLoader classLoader) throws Exception {
		
		if(classLoader==null){
			return null;
		}
		
		WebApp webapp = new WebApp();
		
		URL urls[] = classLoader.getURLs();
		
		List<String> list = new ArrayList<String>();
		
		for (URL url : urls) {
			
			if("file".equals(url.getProtocol())){
				
				if(url.getPath().endsWith("WEB-INF/lib/") || url.getPath().endsWith("WEB-INF/classes/")){
					
					File file = null;
					try {
						file = new File(url.toURI());
					} catch(Exception e){
		                URI uri = new URI(url.toString());
		                if (uri.getAuthority()==null) 
		                    file = new File(uri);
		                else
		                    file = new File("//"+uri.getAuthority()+url.getFile());

					}
					
					if(file!=null && file.isDirectory()){
						list.addAll(listFilesAndFolders(file.getCanonicalPath(),0));
					}
				}
				
			} else if("jar".equals(url.getProtocol())){
				
				String spec = url.getFile();

				int separator = spec.indexOf("!/");
				/*
				 * REMIND: we don't handle nested JAR URLs
				 */
				if (separator == -1) {
				    throw new MalformedURLException("no !/ found in url spec:" + spec);
				}
				
				String filename = spec.substring("file:/".length(),separator);
				
				JarFile jar = new JarFile(filename, false);
				
				Enumeration<JarEntry> en = jar.entries();
				
				while (en.hasMoreElements()) {
					JarEntry jarEntry = en.nextElement();
					
					String classname = jarEntry.getName();
					
					if(classname.endsWith(".class")){
						classname = classname.replace('\\', '/').replace('/', '.').replace('$', '.');
						list.add(classname);
					}
				}
				
				jar.close();
				
			}
			
		}
		
		logger.finest("Number of classes to check for Annotation : " + list.size());
		
		for (String classname : list) {
			try {
				parseAnnotation(classLoader.loadClass(classname.substring(0,classname.indexOf(".class"))), webapp);
			} catch(Throwable t){
			}
		}
		
		return webapp;
	}

}
