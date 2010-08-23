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
import java.util.ArrayList;

/**
 * This class represent a web.xml.  
 * 
 * web-app 2.2, 2.3, 2.4, 2.5 and 3.0 are supported
 * 
 * This is a generic class for all web-app versions.  You will have
 * to call the methods that are supported by your web.xml version.
 * 
 * To understand which informations will be available, 
 * please check the dtd or schemas.
 * 
 * web-app 2.2
 * http://java.sun.com/j2ee/dtds/web-app_2_2.dtd
 * 
 * web-app 2.3
 * http://java.sun.com/dtd/web-app_2_3.dtd
 * 
 * web-app 2.4
 * http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd
 * 
 * web-app 2.5
 * http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd
 * 
 * web-app 3.0
 * http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd
 * 
 */
public class WebApp {

	public boolean metadataComplete;
	public List<Icon> icon = new ArrayList<Icon>(0);
	public List<String> displayName = new ArrayList<String>(0);
	public List<String> description = new ArrayList<String>(0);
	public boolean distributable;
	public List<ContextParam> contextParam = new ArrayList<ContextParam>(0);
	public List<Filter> filter = new ArrayList<Filter>(0);
	public List<FilterMapping> filterMapping = new ArrayList<FilterMapping>(0);
	public List<Listener> listener = new ArrayList<Listener>(0);
	public List<Servlet> servlet = new ArrayList<Servlet>(0);
	public List<ServletMapping> servletMapping = new ArrayList<ServletMapping>(0);
	public List<SessionConfig> sessionConfig = new ArrayList<SessionConfig>(0);
	public List<MimeMapping> mimeMapping = new ArrayList<MimeMapping>(0);
	public List<WelcomeFileList> welcomeFileList = new ArrayList<WelcomeFileList>(0);
	public List<ErrorPage> errorPage = new ArrayList<ErrorPage>(0);
	public List<Taglib> taglib = new ArrayList<Taglib>(0);
	public List<ResourceEnvRef> resourceEnvRef = new ArrayList<ResourceEnvRef>(0);
	public List<ResourceRef> resourceRef = new ArrayList<ResourceRef>(0);
	public List<SecurityConstraint> securityConstraint = new ArrayList<SecurityConstraint>(0);
	public List<LoginConfig> loginConfig = new ArrayList<LoginConfig>(0);
	public List<SecurityRole> securityRole = new ArrayList<SecurityRole>(0);
	public List<EnvEntry> envEntry = new ArrayList<EnvEntry>(0);
	public List<EjbRef> ejbRef = new ArrayList<EjbRef>(0);
	public List<EjbLocalRef> ejbLocalRef = new ArrayList<EjbLocalRef>(0);
	public List<JspConfig> jspConfig = new ArrayList<JspConfig>(0);
	public List<ServiceRef> serviceRef = new ArrayList<ServiceRef>(0);
	public List<MessageDestination> messageDestination = new ArrayList<MessageDestination>(0);
	public List<MessageDestinationRef> messageDestinationRef = new ArrayList<MessageDestinationRef>(0);
	public List<PersistenceContextRef> persistenceContextRef = new ArrayList<PersistenceContextRef>(0);
	public List<PersistenceUnitRef> persistenceUnitRef = new ArrayList<PersistenceUnitRef>(0);
	public List<LifecycleCallback> postConstruct = new ArrayList<LifecycleCallback>(0);
	public List<LifecycleCallback> preDestroy = new ArrayList<LifecycleCallback>(0);
	public List<LocaleEncodingMappingList> localeEncodingMappingList = new ArrayList<LocaleEncodingMappingList>(0);

	/**
	 * &lt;icon&gt;
	 *	&lt;small-icon&gt;token&lt;/small-icon&gt;
	 *	&lt;large-icon&gt;token&lt;/large-icon&gt;
	 * &lt;/icon&gt;
	 */
	public List<Icon> getIcon() {
		return icon;
	}

	public void setIcon(List<Icon> icon) {
        if (icon != null) {
		    this.icon = icon;
        }
	}
	
	/**
	 * &lt;display-name&gt;name&lt;/display-name&gt; 
	 */
	public List<String> getDisplayName() {
		return displayName;
	}

	public void setDisplayName(List<String> displayName) {
        if (displayName != null) {
		    this.displayName = displayName;
        }
	}

	/**
	 * &lt;description&gt;description&lt;/description&gt;
	 */
	public List<String> getDescription() {
		return description;
	}

	public void setDescription(List<String> description) {
        if (description != null) {
            this.description = description;
        }
	}
	
	/**
	 * attribute of web-app : metadata-complete="true"
	 * @param complete is completed
	 */
	public void setMetadataComplete(boolean complete){
		metadataComplete = complete;
	}
	
	public boolean getMetadataComplete(){
		return metadataComplete;
	}
	
	/**
	 * &lt;distributable&gt;&lt;/distributable&gt;
	 */
	public boolean getDistributable() {
		return distributable;
	}

	public void setDistributable(boolean distributable) {
		this.distributable = distributable;
	}

	/**
	 * &lt;servlet&gt;
     *  &lt;servlet-name&gt;Hello&lt;/servlet-name&gt;
     *	&lt;servlet-class&gt;ca.sebastiendionne.HelloWorld&lt;/servlet-class&gt;
     *	&lt;load-on-startup&gt;1&lt;/load-on-startup&gt;
  	 * &lt;/servlet&gt;
	 */
	public List<Servlet> getServlet() {
		return servlet;
	}

	public void setServlet(List<Servlet> servlet) {
        if (servlet != null) {
            this.servlet = servlet;
        }
	}

	/**
	 * &lt;filter&gt;
     *   &lt;filter-name&gt;filter&lt;/filter-name&gt;
     *   &lt;filter-class&gt;ca.sebastiendionne.TestFilter&lt;/filter-class&gt;
     *   &lt;init-param&gt;
     *    &lt;param-name&gt;paramName&lt;/param-name&gt;
     *    &lt;param-value&gt;paramValue&lt;/param-value&gt;
     *   &lt;/init-param&gt;
     *  &lt;/filter&gt;
	 */
	public List<Filter> getFilter() {
		return filter;
	}

	public void setFilter(List<Filter> filter) {
        if (filter != null) {
    		this.filter = filter;
        }
	}
	
	/**
	 * &lt;context-param&gt;
	 *	&lt;description&gt;description&lt;/description&gt;
	 *	&lt;param-name&gt;token&lt;/param-name&gt;
	 *  &lt;param-value&gt;value&lt;/param-value&gt;
	 * &lt;/context-param&gt;
	 */
	public List<ContextParam> getContextParam() {
		return contextParam;
	}

	public void setContextParam(List<ContextParam> contextParam) {
        if (contextParam != null) {
    		this.contextParam = contextParam;
        }
	}
	
	/**
	 * &lt;ejb-local-ref&gt;
	 *	&lt;description&gt;description&lt;/description&gt;
	 *	&lt;ejb-ref-name&gt;token&lt;/ejb-ref-name&gt;
	 *	&lt;ejb-ref-type&gt;Session&lt;/ejb-ref-type&gt;
	 *	&lt;local-home&gt;token&lt;/local-home&gt;
	 *	&lt;local&gt;token&lt;/local&gt;
	 *	&lt;ejb-link&gt;token&lt;/ejb-link&gt;
	 *	&lt;mapped-name&gt;string&lt;/mapped-name&gt;
	 *	&lt;injection-target&gt;
	 *		&lt;injection-target-class&gt;token&lt;/injection-target-class&gt;
	 *		&lt;injection-target-name&gt;$&lt;/injection-target-name&gt;
	 *	&lt;/injection-target&gt;
	 * &lt;/ejb-local-ref&gt;
	 */
	public List<EjbLocalRef> getEjbLocalRef() {
		return ejbLocalRef;
	}

	public void setEjbLocalRef(List<EjbLocalRef> ejbLocalRef) {
        if (ejbLocalRef != null) {
    		this.ejbLocalRef = ejbLocalRef;
        }
	}

	/**
	 * &lt;ejb-ref&gt;
	 *  &lt;description&gt;description&lt;/description&gt;
	 *	&lt;ejb-ref-name&gt;token&lt;/ejb-ref-name&gt;
	 *	&lt;ejb-ref-type&gt;Session&lt;/ejb-ref-type&gt;
	 *	&lt;home&gt;token&lt;/home&gt;
	 *	&lt;remote&gt;token&lt;/remote&gt;
	 *	&lt;ejb-link&gt;token&lt;/ejb-link&gt;
	 *	&lt;mapped-name&gt;string&lt;/mapped-name&gt;
	 *	&lt;injection-target&gt;
	 *		&lt;injection-target-class&gt;token&lt;/injection-target-class&gt;
	 *		&lt;injection-target-name&gt;$&lt;/injection-target-name&gt;
	 *	&lt;/injection-target&gt;
	 * &lt;/ejb-ref&gt;
	 */
	public List<EjbRef> getEjbRef() {
		return ejbRef;
	}

	public void setEjbRef(List<EjbRef> ejbRef) {
        if (ejbRef != null) {
    		this.ejbRef = ejbRef;
        }
	}
	
	/**
	 * &lt;env-entry&gt;
	 *	&lt;description&gt;description&lt;/description&gt;
	 *	&lt;env-entry-name&gt;token&lt;/env-entry-name&gt;
	 *	&lt;env-entry-type&gt;java.lang.Double&lt;/env-entry-type&gt;
	 *	&lt;env-entry-value&gt;string&lt;/env-entry-value&gt;
	 *	&lt;mapped-name&gt;string&lt;/mapped-name&gt;
	 *	&lt;injection-target&gt;
	 *		&lt;injection-target-class&gt;token&lt;/injection-target-class&gt;
	 *		&lt;injection-target-name&gt;$&lt;/injection-target-name&gt;
	 *	&lt;/injection-target&gt;
	 * &lt;/env-entry&gt;
	 */
	public List<EnvEntry> getEnvEntry() {
		return envEntry;
	}

	public void setEnvEntry(List<EnvEntry> envEntry) {
        if (envEntry != null) {
    		this.envEntry = envEntry;
        }
	}
	
	/**
	 * &lt;error-page&gt;
	 *	&lt;error-code&gt;404&lt;/error-code&gt;
	 *	&lt;exception-type&gt;token&lt;/exception-type&gt;
	 *	&lt;location&gt;/&lt;/location&gt;
	 * &lt;/error-page&gt;
	 */
	public List<ErrorPage> getErrorPage() {
		return errorPage;
	}

	public void setErrorPage(List<ErrorPage> errorPage) {
        if (errorPage != null) {
    		this.errorPage = errorPage;
        }
	}
	
	/**
	 * &lt;filter-mapping&gt;
	 *	&lt;filter-name/&gt;
	 *	&lt;url-pattern&gt;string&lt;/url-pattern&gt;
	 *	&lt;servlet-name/&gt;
	 *	&lt;url-pattern&gt;string&lt;/url-pattern&gt;
	 *	&lt;servlet-name/&gt;
	 *	&lt;url-pattern&gt;string&lt;/url-pattern&gt;
	 *	&lt;servlet-name/&gt;
	 *	&lt;dispatcher&gt;ERROR&lt;/dispatcher&gt;
	 * &lt;/filter-mapping&gt;
	 */
	public List<FilterMapping> getFilterMapping() {
		return filterMapping;
	}

	public void setFilterMapping(List<FilterMapping> filterMapping) {
        if (filterMapping != null) {
    		this.filterMapping = filterMapping;
        }
	}

	/**
	 * &lt;listener&gt;
	 *	&lt;description&gt;description&lt;/description&gt;
	 *	&lt;display-name&gt;token&lt;/display-name&gt;
	 *	&lt;icon&gt;
	 *		&lt;small-icon&gt;token&lt;/small-icon&gt;
	 *		&lt;large-icon&gt;token&lt;/large-icon&gt;
	 *	&lt;/icon&gt;
	 *	&lt;listener-class&gt;token&lt;/listener-class&gt;
	 * &lt;/listener&gt;
	 */
	public List<Listener> getListener() {
		return listener;
	}

	public void setListener(List<Listener> listener) {
        if (listener != null) {
    		this.listener = listener;
        }
	}
	
	/**
	 * &lt;login-config&gt;
	 *	&lt;auth-method&gt;token&lt;/auth-method&gt;
	 *	&lt;realm-name&gt;token&lt;/realm-name&gt;
	 *	&lt;form-login-config&gt;
	 *		&lt;form-login-page&gt;/&lt;/form-login-page&gt;
	 *		&lt;form-error-page&gt;/&lt;/form-error-page&gt;
	 *	&lt;/form-login-config&gt;
	 * &lt;/login-config&gt;
	 */
	public List<LoginConfig> getLoginConfig() {
		return loginConfig;
	}

	public void setLoginConfig(List<LoginConfig> loginConfig) {
        if (loginConfig != null) {
    		this.loginConfig = loginConfig;
        }
	}
	
	/**
	 * &lt;mime-mapping&gt;
	 *	&lt;extension&gt;token&lt;/extension&gt;
	 *	&lt;mime-type&gt;!/!&lt;/mime-type&gt;
	 * &lt;/mime-mapping&gt;
	 */
	public List<MimeMapping> getMimeMapping() {
		return mimeMapping;
	}

	public void setMimeMapping(List<MimeMapping> mimeMapping) {
        if (mimeMapping != null) {
    		this.mimeMapping = mimeMapping;
        }
	}
	
	/**
	 * &lt;resource-env-ref&gt;
	 *	&lt;description&gt;description&lt;/description&gt;
	 *	&lt;resource-env-ref-name&gt;token&lt;/resource-env-ref-name&gt;
	 *	&lt;resource-env-ref-type&gt;token&lt;/resource-env-ref-type&gt;
	 *	&lt;mapped-name&gt;string&lt;/mapped-name&gt;
	 *	&lt;injection-target&gt;
	 *		&lt;injection-target-class&gt;token&lt;/injection-target-class&gt;
	 *		&lt;injection-target-name&gt;$&lt;/injection-target-name&gt;
	 *	&lt;/injection-target&gt;
	 * &lt;/resource-env-ref&gt;
	 */
	public List<ResourceEnvRef> getResourceEnvRef() {
		return resourceEnvRef;
	}

	public void setResourceEnvRef(List<ResourceEnvRef> resourceEnvRef) {
        if (resourceEnvRef != null) {
    		this.resourceEnvRef = resourceEnvRef;
        }
	}
	
	/**
	 * &lt;security-constraint&gt;
	 *	&lt;display-name&gt;displayname&lt;/display-name&gt;
	 *	&lt;web-resource-collection&gt;
	 *		&lt;web-resource-name&gt;token&lt;/web-resource-name&gt;
	 *		&lt;description&gt;description&lt;/description&gt;
	 *		&lt;url-pattern&gt;string&lt;/url-pattern&gt;
	 *		&lt;http-method&gt;t&lt;/http-method&gt;
	 *	&lt;/web-resource-collection&gt;
	 *	&lt;user-data-constraint&gt;
	 *		&lt;description&gt;description&lt;/description&gt;
	 *		&lt;transport-guarantee&gt;CONFIDENTIAL&lt;/transport-guarantee&gt;
	 *	&lt;/user-data-constraint&gt;
	 * &lt;/security-constraint&gt;
	 */
	public List<SecurityConstraint> getSecurityConstraint() {
		return securityConstraint;
	}

	public void setSecurityConstraint(List<SecurityConstraint> securityConstraint) {
        if (securityConstraint != null) {
    		this.securityConstraint = securityConstraint;
        }
	}
	
	/**
	 * &lt;servlet-mapping&gt;
	 *	&lt;servlet-name&gt;servletname&lt;/servlet-name&gt;
	 *	&lt;url-pattern&gt;/*.jsp&lt;/url-pattern&gt;
	 * &lt;/servlet-mapping&gt;
	 */
	public List<ServletMapping> getServletMapping() {
		return servletMapping;
	}

	public void setServletMapping(List<ServletMapping> servletMapping) {
        if (servletMapping != null) {
    		this.servletMapping = servletMapping;
        }
	}
	
	/**
	 * &lt;session-config&gt;
	 *	&lt;session-timeout&gt;120&lt;/session-timeout&gt;
	 *	&lt;cookie-config&gt;
	 *		&lt;name&gt;name&lt;/name&gt;
	 *		&lt;domain&gt;domain&lt;/domain&gt;
	 *		&lt;path&gt;path&lt;/path&gt;
	 *		&lt;comment&gt;comment&lt;/comment&gt;
	 *		&lt;http-only&gt;true&lt;/http-only&gt;
	 *		&lt;secure&gt;true&lt;/secure&gt;
	 *	&lt;/cookie-config&gt;
	 *	&lt;tracking-mode&gt;SSL&lt;/tracking-mode&gt;
	 * &lt;/session-config&gt;
	 */
	public List<SessionConfig> getSessionConfig() {
		return sessionConfig;
	}

	public void setSessionConfig(List<SessionConfig> sessionConfig) {
        if (sessionConfig != null) {
    		this.sessionConfig = sessionConfig;
        }
	}
	
	/**
	 * &lt;taglib&gt;
	 *		&lt;taglib-uri&gt;uri&lt;/taglib-uri&gt;
	 *		&lt;taglib-location&gt;location&lt;/taglib-location&gt;
	 *	&lt;/taglib&gt;
	 */
	public List<Taglib> getTaglib() {
		return taglib;
	}

	public void setTaglib(List<Taglib> taglib) {
        if (taglib != null) {
    		this.taglib = taglib;
        }
	}
	
	/**
	 * &lt;security-role&gt;
	 *	&lt;description&gt;description&lt;/description&gt;
	 *	&lt;role-name&gt;token&lt;/role-name&gt;
	 * &lt;/security-role&gt;
	 */
	public List<SecurityRole> getSecurityRole() {
		return securityRole;
	}

	public void setSecurityRole(List<SecurityRole> securityRole) {
        if (securityRole != null) {
    		this.securityRole = securityRole;
        }
	}

	/**
	 * &lt;resource-ref&gt;
	 *	&lt;description&gt;description&lt;/description&gt;
	 *	&lt;res-ref-name&gt;token&lt;/res-ref-name&gt;
	 *	&lt;res-type&gt;token&lt;/res-type&gt;
	 *	&lt;res-auth&gt;Container&lt;/res-auth&gt;
	 *	&lt;res-sharing-scope&gt;Unshareable&lt;/res-sharing-scope&gt;
	 *	&lt;mapped-name&gt;string&lt;/mapped-name&gt;
	 *	&lt;injection-target&gt;
	 *		&lt;injection-target-class&gt;token&lt;/injection-target-class&gt;
	 *		&lt;injection-target-name&gt;$&lt;/injection-target-name&gt;
	 *	&lt;/injection-target&gt;
	 * &lt;/resource-ref&gt;
	 */
	public List<ResourceRef> getResourceRef() {
		return resourceRef;
	}

	public void setResourceRef(List<ResourceRef> resourceRef) {
        if (resourceRef != null) {
    		this.resourceRef = resourceRef;
        }
	}
	
	/**
	 * &lt;welcome-file-list&gt;
	 *	&lt;welcome-file&gt;index.jsp&lt;/welcome-file&gt;
	 *	&lt;welcome-file&gt;index.html&lt;/welcome-file&gt;
	 * &lt;/welcome-file-list&gt;
	 */
	public List<WelcomeFileList> getWelcomeFileList() {
		return welcomeFileList;
	}

	public void setWelcomeFileList(List<WelcomeFileList> welcomeFileList) {
        if (welcomeFileList != null) {
    		this.welcomeFileList = welcomeFileList;
        }
	}
	
	/**
	 * &lt;jsp-config&gt;
	 *	&lt;taglib&gt;
	 *		&lt;taglib-uri&gt;uri&lt;/taglib-uri&gt;
	 *		&lt;taglib-location&gt;localtion&lt;/taglib-location&gt;
	 *	&lt;/taglib&gt;
	 *	&lt;jsp-property-group&gt;
	 *		&lt;description&gt;description&lt;/description&gt;
	 *		&lt;display-name&gt;displayname&lt;/display-name&gt;
	 *		&lt;icon&gt;
	 *			&lt;small-icon&gt;token&lt;/small-icon&gt;
	 *			&lt;large-icon&gt;token&lt;/large-icon&gt;
	 *		&lt;/icon&gt;
	 *		&lt;url-pattern&gt;string&lt;/url-pattern&gt;
	 *		&lt;el-ignored&gt;true&lt;/el-ignored&gt;
	 *		&lt;page-encoding&gt;token&lt;/page-encoding&gt;
	 *		&lt;scripting-invalid&gt;true&lt;/scripting-invalid&gt;
	 *		&lt;is-xml&gt;true&lt;/is-xml&gt;
	 *		&lt;include-prelude&gt;token&lt;/include-prelude&gt;
	 *		&lt;include-coda&gt;token&lt;/include-coda&gt;
	 *		&lt;deferred-syntax-allowed-as-literal&gt;true&lt;/deferred-syntax-allowed-as-literal&gt;
	 *		&lt;trim-directive-whitespaces&gt;true&lt;/trim-directive-whitespaces&gt;
	 *		&lt;default-content-type&gt;token&lt;/default-content-type&gt;
	 *		&lt;buffer&gt;token&lt;/buffer&gt;
	 *		&lt;error-on-undeclared-namespace&gt;true&lt;/error-on-undeclared-namespace&gt;
	 *	&lt;/jsp-property-group&gt;
	 * &lt;/jsp-config&gt;
	 */
	public List<JspConfig> getJspConfig() {
		return jspConfig;
	}

	public void setJspConfig(List<JspConfig> jspConfig) {
        if (jspConfig != null) {
    		this.jspConfig = jspConfig;
        }
	}

	/**
	 * &lt;service-ref&gt;
	 *	&lt;description&gt;description&lt;/description&gt;
	 *	&lt;display-name&gt;token&lt;/display-name&gt;
	 *	&lt;icon&gt;
	 *		&lt;small-icon&gt;token&lt;/small-icon&gt;
	 *		&lt;large-icon&gt;token&lt;/large-icon&gt;
	 *	&lt;/icon&gt;
	 *	&lt;service-ref-name&gt;token&lt;/service-ref-name&gt;
	 *	&lt;service-interface&gt;token&lt;/service-interface&gt;
	 *	&lt;service-ref-type&gt;token&lt;/service-ref-type&gt;
	 *	&lt;wsdl-file&gt; URI&lt;/wsdl-file&gt;
	 *	&lt;jaxrpc-mapping-file&gt;token&lt;/jaxrpc-mapping-file&gt;
	 *	&lt;service-qname&gt;pref:name&lt;/service-qname&gt;
	 *	&lt;port-component-ref&gt;
	 *		&lt;service-endpoint-interface&gt;token&lt;/service-endpoint-interface&gt;
	 *		&lt;enable-mtom&gt;true&lt;/enable-mtom&gt;
	 *		&lt;port-component-link&gt;token&lt;/port-component-link&gt;
	 *	&lt;/port-component-ref&gt;
	 *	&lt;handler&gt;
	 *		&lt;description&gt;description&lt;/description&gt;
	 *		&lt;display-name&gt;token&lt;/display-name&gt;
	 *		&lt;icon&gt;
	 *			&lt;small-icon&gt;token&lt;/small-icon&gt;
	 *			&lt;large-icon&gt;token&lt;/large-icon&gt;
	 *		&lt;/icon&gt;
	 *		&lt;handler-name&gt;token&lt;/handler-name&gt;
	 *		&lt;handler-class&gt;token&lt;/handler-class&gt;
	 *		&lt;init-param&gt;
	 *			&lt;description&gt;string&lt;/description&gt;
	 *			&lt;param-name&gt;token&lt;/param-name&gt;
	 *			&lt;param-value&gt;string&lt;/param-value&gt;
	 *		&lt;/init-param&gt;
	 *		&lt;soap-header&gt;pref:name&lt;/soap-header&gt;
	 *		&lt;soap-role&gt;token&lt;/soap-role&gt;
	 *		&lt;port-name&gt;token&lt;/port-name&gt;
	 *	&lt;/handler&gt;
	 *	&lt;handler-chains&gt;
	 *		&lt;handler-chain&gt;
	 *			&lt;service-name-pattern&gt;*&lt;/service-name-pattern&gt;
	 *			&lt;port-name-pattern&gt;*&lt;/port-name-pattern&gt;
	 *			&lt;protocol-bindings/&gt;
	 *			&lt;handler&gt;
	 *				&lt;description&gt;string&lt;/description&gt;
	 *				&lt;display-name&gt;token&lt;/display-name&gt;
	 *				&lt;icon&gt;
	 *					&lt;small-icon&gt;token&lt;/small-icon&gt;
	 *					&lt;large-icon&gt;token&lt;/large-icon&gt;
	 *				&lt;/icon&gt;
	 *				&lt;handler-name&gt;token&lt;/handler-name&gt;
	 *				&lt;handler-class&gt;token&lt;/handler-class&gt;
	 *				&lt;init-param&gt;
	 *					&lt;description&gt;string&lt;/description&gt;
	 *					&lt;param-name&gt;token&lt;/param-name&gt;
	 *					&lt;param-value&gt;string&lt;/param-value&gt;
	 *				&lt;/init-param&gt;
	 *				&lt;soap-header&gt;pref:name&lt;/soap-header&gt;
	 *				&lt;soap-role&gt;token&lt;/soap-role&gt;
	 *				&lt;port-name&gt;token&lt;/port-name&gt;
	 *			&lt;/handler&gt;
	 *		&lt;/handler-chain&gt;
	 *	&lt;/handler-chains&gt;
	 *	&lt;mapped-name&gt;string&lt;/mapped-name&gt;
	 *	&lt;injection-target&gt;
	 *		&lt;injection-target-class&gt;token&lt;/injection-target-class&gt;
	 *		&lt;injection-target-name&gt;$&lt;/injection-target-name&gt;
	 *	&lt;/injection-target&gt;
	 * &lt;/service-ref&gt;
	 */
	public List<ServiceRef> getServiceRef() {
		return serviceRef;
	}

	public void setServiceRef(List<ServiceRef> serviceRef) {
        if (serviceRef != null) {
    		this.serviceRef = serviceRef;
        }
	}
	
	/**
	 * &lt;message-destination&gt;
	 *	&lt;description&gt;string&lt;/description&gt;
	 *	&lt;display-name&gt;token&lt;/display-name&gt;
	 *	&lt;icon&gt;
	 *		&lt;small-icon&gt;token&lt;/small-icon&gt;
	 *		&lt;large-icon&gt;token&lt;/large-icon&gt;
	 *	&lt;/icon&gt;
	 *	&lt;message-destination-name&gt;token&lt;/message-destination-name&gt;
	 *	&lt;mapped-name&gt;string&lt;/mapped-name&gt;
	 * &lt;/message-destination&gt;
	 */
	public List<MessageDestination> getMessageDestination() {
		return messageDestination;
	}

	public void setMessageDestination(List<MessageDestination> messageDestination) {
        if (messageDestination != null) {
    		this.messageDestination = messageDestination;
        }
	}

	/**
	 * &lt;message-destination-ref&gt;
	 *	&lt;description&gt;string&lt;/description&gt;
	 *	&lt;message-destination-ref-name&gt;token&lt;/message-destination-ref-name&gt;
	 *	&lt;message-destination-type&gt;token&lt;/message-destination-type&gt;
	 *	&lt;message-destination-usage&gt;ConsumesProduces&lt;/message-destination-usage&gt;
	 *	&lt;message-destination-link&gt;token&lt;/message-destination-link&gt;
	 *	&lt;mapped-name&gt;string&lt;/mapped-name&gt;
	 *	&lt;injection-target&gt;
	 *		&lt;injection-target-class&gt;token&lt;/injection-target-class&gt;
	 *		&lt;injection-target-name&gt;$&lt;/injection-target-name&gt;
	 *	&lt;/injection-target&gt;
	 * &lt;/message-destination-ref&gt;
	 */
	public List<MessageDestinationRef> getMessageDestinationRef() {
		return messageDestinationRef;
	}

	public void setMessageDestinationRef(List<MessageDestinationRef> messageDestinationRef) {
        if (messageDestinationRef != null) {
    		this.messageDestinationRef = messageDestinationRef;
        }
	}
	
	/**
	 * &lt;persistence-context-ref&gt;
	 *	&lt;description&gt;string&lt;/description&gt;
	 *	&lt;persistence-context-ref-name&gt;token&lt;/persistence-context-ref-name&gt;
	 *	&lt;persistence-unit-name&gt;token&lt;/persistence-unit-name&gt;
	 *	&lt;persistence-context-type&gt;Extended&lt;/persistence-context-type&gt;
	 *	&lt;persistence-property&gt;
	 *		&lt;name&gt;string&lt;/name&gt;
	 *		&lt;value&gt;string&lt;/value&gt;
	 *	&lt;/persistence-property&gt;
	 *	&lt;mapped-name&gt;string&lt;/mapped-name&gt;
	 *	&lt;injection-target&gt;
	 *		&lt;injection-target-class&gt;token&lt;/injection-target-class&gt;
	 *		&lt;injection-target-name&gt;$&lt;/injection-target-name&gt;
	 *	&lt;/injection-target&gt;
	 * &lt;/persistence-context-ref&gt;
	 */
	public List<PersistenceContextRef> getPersistenceContextRef() {
		return persistenceContextRef;
	}

	public void setPersistenceContextRef(List<PersistenceContextRef> persistenceContextRef) {
        if (persistenceContextRef != null) {
    		this.persistenceContextRef = persistenceContextRef;
        }
	}

	/**
	 * &lt;persistence-unit-ref&gt;
	 *	&lt;description&gt;string&lt;/description&gt;
	 *	&lt;persistence-unit-ref-name&gt;token&lt;/persistence-unit-ref-name&gt;
	 *	&lt;persistence-unit-name&gt;token&lt;/persistence-unit-name&gt;
	 *	&lt;mapped-name&gt;string&lt;/mapped-name&gt;
	 *	&lt;injection-target&gt;
	 *		&lt;injection-target-class&gt;token&lt;/injection-target-class&gt;
	 *		&lt;injection-target-name&gt;$&lt;/injection-target-name&gt;
	 *	&lt;/injection-target&gt;
	 * &lt;/persistence-unit-ref&gt;
	 */
	public List<PersistenceUnitRef> getPersistenceUnitRef() {
		return persistenceUnitRef;
	}

	public void setPersistenceUnitRef(List<PersistenceUnitRef> persistenceUnitRef) {
        if (persistenceUnitRef != null) {
    		this.persistenceUnitRef = persistenceUnitRef;
        }
	}
	
	/**
	 * &lt;post-construct&gt;
	 *	&lt;lifecycle-callback-class&gt;token&lt;/lifecycle-callback-class&gt;
	 *	&lt;lifecycle-callback-method&gt;$&lt;/lifecycle-callback-method&gt;
	 * &lt;/post-construct&gt;
	 */
	public List<LifecycleCallback> getPostConstruct() {
		return postConstruct;
	}
	
	public void setPostConstruct(List<LifecycleCallback> postConstruct) {
        if (postConstruct != null) {
    		this.postConstruct = postConstruct;
        }
	}
	
	/**
	 * &lt;pre-destroy&gt;
	 *	&lt;lifecycle-callback-class&gt;token&lt;/lifecycle-callback-class&gt;
	 *	&lt;lifecycle-callback-method&gt;$&lt;/lifecycle-callback-method&gt;
	 * &lt;/pre-destroy&gt;
	 */
	public List<LifecycleCallback> getPreDestroy() {
		return preDestroy;
	}

	public void setPreDestroy(List<LifecycleCallback> preDestroy) {
        if (preDestroy != null) {
    		this.preDestroy = preDestroy;
        }
	}

	/**
	 * &lt;locale-encoding-mapping-list&gt;
	 *	&lt;locale-encoding-mapping&gt;
	 *		&lt;locale&gt;st_ri&lt;/locale&gt;
	 *		&lt;encoding&gt;s&lt;/encoding&gt;
	 *	&lt;/locale-encoding-mapping&gt;
	 * &lt;/locale-encoding-mapping-list&gt;
	 */
	public List<LocaleEncodingMappingList> getLocaleEncodingMappingList() {
		return localeEncodingMappingList;
	}

	public void setLocaleEncodingMappingList(List<LocaleEncodingMappingList> localeEncodingMappingList) {
        if (localeEncodingMappingList != null) {
    		this.localeEncodingMappingList = localeEncodingMappingList;
        }
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<WebApp>").append("\n");

		if (distributable) {
			buffer.append("<distributable>").append("</distributable>").append("\n");
		}

		if (icon != null && icon.size() > 0) {
			List<Icon> list = icon;

			for (Icon item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (description != null && description.size() > 0) {
			List<String> list = description;

			for (String item : list) {
				buffer.append("<description>").append(item).append("</description>").append("\n");
			}
		}

		if (displayName != null && displayName.size() > 0) {
			List<String> list = displayName;

			for (String item : list) {
				buffer.append("<displayName>").append(item).append("</displayName>").append("\n");
			}
		}

		if (contextParam != null) {
			List<ContextParam> list = contextParam;

			for (ContextParam item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (filter != null) {
			List<Filter> list = filter;

			for (Filter item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (filterMapping != null) {
			List<FilterMapping> list = filterMapping;

			for (FilterMapping item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (listener != null) {
			List<Listener> list = listener;

			for (Listener item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (servlet != null) {
			List<Servlet> list = servlet;

			for (Servlet item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (servletMapping != null) {
			List<ServletMapping> list = servletMapping;

			for (ServletMapping item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (sessionConfig != null) {
			List<SessionConfig> list = sessionConfig;

			for (SessionConfig item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (mimeMapping != null) {
			List<MimeMapping> list = mimeMapping;

			for (MimeMapping item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (welcomeFileList != null) {
			List<WelcomeFileList> list = welcomeFileList;

			for (WelcomeFileList item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (errorPage != null) {
			List<ErrorPage> list = errorPage;

			for (ErrorPage item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (taglib != null) {
			List<Taglib> list = taglib;

			for (Taglib item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (resourceRef != null) {
			List<ResourceRef> list = resourceRef;

			for (ResourceRef item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (resourceEnvRef != null) {
			List<ResourceEnvRef> list = resourceEnvRef;

			for (ResourceEnvRef item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (securityConstraint != null) {
			List<SecurityConstraint> list = securityConstraint;

			for (SecurityConstraint item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (loginConfig != null) {
			List<LoginConfig> list = loginConfig;

			for (LoginConfig item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (securityRole != null) {
			List<SecurityRole> list = securityRole;

			for (SecurityRole item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (ejbRef != null) {
			List<EjbRef> list = ejbRef;

			for (EjbRef item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (ejbLocalRef != null) {
			List<EjbLocalRef> list = ejbLocalRef;

			for (EjbLocalRef item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (envEntry != null) {
			List<EnvEntry> list = envEntry;

			for (EnvEntry item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (jspConfig != null) {
			List<JspConfig> list = jspConfig;

			for (JspConfig item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (serviceRef != null) {
			List<ServiceRef> list = serviceRef;

			for (ServiceRef item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (messageDestination != null) {
			List<MessageDestination> list = messageDestination;

			for (MessageDestination item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (messageDestinationRef != null) {
			List<MessageDestinationRef> list = messageDestinationRef;

			for (MessageDestinationRef item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (persistenceContextRef != null) {
			List<PersistenceContextRef> list = persistenceContextRef;

			for (PersistenceContextRef item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (persistenceUnitRef != null) {
			List<PersistenceUnitRef> list = persistenceUnitRef;

			for (PersistenceUnitRef item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (postConstruct != null) {
			List<LifecycleCallback> list = postConstruct;
			
			buffer.append("<post-construct>").append("\n");
			for (LifecycleCallback item : list) {
				buffer.append(item).append("\n");
			}
			buffer.append("</post-construct>").append("\n");
		}

		if (preDestroy != null) {
			List<LifecycleCallback> list = preDestroy;

			buffer.append("<pre-destroy>").append("\n");
			for (LifecycleCallback item : list) {
				buffer.append(item).append("\n");
			}
			buffer.append("</pre-destroy>").append("\n");
		}

		if (localeEncodingMappingList != null) {
			List<LocaleEncodingMappingList> list = localeEncodingMappingList;

			for (LocaleEncodingMappingList item : list) {
				buffer.append(item).append("\n");
			}
		}

		buffer.append("</WebApp>");
		return buffer.toString();
	}

    public WebApp mergeWith(WebApp with) {
        this.contextParam.addAll(with.contextParam);
        this.description.addAll(with.description);
        this.displayName.addAll(with.displayName);
        this.ejbLocalRef.addAll(with.ejbLocalRef);
        this.ejbRef.addAll(with.ejbRef);
        this.envEntry.addAll(with.envEntry);
        this.errorPage.addAll(with.errorPage);
        this.filter.addAll(with.filter);
        this.filterMapping.addAll(with.filterMapping);
        this.icon.addAll(with.icon);
        this.jspConfig.addAll(with.jspConfig);
        this.listener.addAll(with.listener);
        this.localeEncodingMappingList.addAll(with.localeEncodingMappingList);
        this.loginConfig.addAll(with.loginConfig);
        this.messageDestination.addAll(with.messageDestination);
        this.messageDestinationRef.addAll(with.messageDestinationRef);
        this.mimeMapping.addAll(with.mimeMapping);
        this.persistenceContextRef.addAll(with.persistenceContextRef);
        this.persistenceUnitRef.addAll(with.persistenceUnitRef);
        this.postConstruct.addAll(with.postConstruct);
        this.preDestroy.addAll(with.preDestroy);
        this.resourceEnvRef.addAll(with.resourceEnvRef);
        this.resourceRef.addAll(with.resourceRef);
        this.securityConstraint.addAll(with.securityConstraint);
        this.securityRole.addAll(with.securityRole);
        this.serviceRef.addAll(with.serviceRef);
        this.servlet.addAll(with.servlet);
        this.servletMapping.addAll(with.servletMapping);
        this.sessionConfig.addAll(with.sessionConfig);
        this.taglib.addAll(with.taglib);
        this.welcomeFileList.addAll(with.welcomeFileList);
        return this;
    }
    
    /**
     * Merge with annotations attributes that are not already define
     * 
     * Types that are used by Annotations
		EnvEntry
		LifecycleCallback
		MessageDestinationRef
		PortComponentRef
		ResourceRef
		SecurityRole
		ServiceRef
		Servlet
		
		EJB and persistance not yet supported
     * 
     * @param webAppAnon WebApp that contains Annotations
     * @return merge webapp
     */
    public WebApp mergeWithAnnotations(WebApp webAppAnon) {
    	if(webAppAnon==null){
    		return this;
    	}
    	
        List<EnvEntry> listEnvEntry = new ArrayList<EnvEntry>();
		for (EnvEntry anon : webAppAnon.envEntry) {
			boolean found = false;
			for (EnvEntry item : envEntry) {
				if(anon.getEnvEntryName().equalsIgnoreCase(item.getEnvEntryName())){
					found = true;
					break;
				}
			}
			if(!found){
				listEnvEntry.add(anon);
			}
		}
        
        this.envEntry.addAll(listEnvEntry);
        
        List<MessageDestination> listMessageDestination = new ArrayList<MessageDestination>();
		for (MessageDestination anon : webAppAnon.messageDestination) {
			boolean found = false;
			for (MessageDestination item : messageDestination) {
				if(anon.getMessageDestinationName().equalsIgnoreCase(item.getMessageDestinationName())){
					found = true;
					break;
				}
			}
			if(!found){
				listMessageDestination.add(anon);
			}
		}
        
        this.messageDestination.addAll(listMessageDestination);
        
        List<MessageDestinationRef> listMessageDestinationRef = new ArrayList<MessageDestinationRef>();
		for (MessageDestinationRef anon : webAppAnon.messageDestinationRef) {
			boolean found = false;
			for (MessageDestinationRef item : messageDestinationRef) {
				if(anon.getMessageDestinationRefName().equalsIgnoreCase(item.getMessageDestinationRefName())){
					found = true;
					break;
				}
			}
			if(!found){
				listMessageDestinationRef.add(anon);
			}
		}
        
        this.messageDestinationRef.addAll(listMessageDestinationRef);

        List<LifecycleCallback> listLifecycleCallbackPost = new ArrayList<LifecycleCallback>();
		for (LifecycleCallback anon : webAppAnon.postConstruct) {
			boolean found = false;
			for (LifecycleCallback item : postConstruct) {
				if(anon.getLifecycleCallbackMethod().equalsIgnoreCase(item.getLifecycleCallbackMethod())){
					found = true;
					break;
				}
			}
			if(!found){
				listLifecycleCallbackPost.add(anon);
			}
		}
		
        this.postConstruct.addAll(listLifecycleCallbackPost);
        
        List<LifecycleCallback> listLifecycleCallbackPre = new ArrayList<LifecycleCallback>();
		for (LifecycleCallback anon : webAppAnon.preDestroy) {
			boolean found = false;
			for (LifecycleCallback item : preDestroy) {
				if(anon.getLifecycleCallbackMethod().equalsIgnoreCase(item.getLifecycleCallbackMethod())){
					found = true;
					break;
				}
			}
			if(!found){
				listLifecycleCallbackPre.add(anon);
			}
		}
        
        this.preDestroy.addAll(listLifecycleCallbackPre);
        
        List<ResourceEnvRef> listResourceEnvRef = new ArrayList<ResourceEnvRef>();
		for (ResourceEnvRef anon : webAppAnon.resourceEnvRef) {
			boolean found = false;
			for (ResourceEnvRef item : resourceEnvRef) {
				if(anon.getResourceEnvRefName().equalsIgnoreCase(item.getResourceEnvRefName())){
					found = true;
					break;
				}
			}
			if(!found){
				listResourceEnvRef.add(anon);
			}
		}
        
        this.resourceEnvRef.addAll(listResourceEnvRef);
        
        List<ResourceRef> listResourceRef = new ArrayList<ResourceRef>();
		for (ResourceRef anon : webAppAnon.resourceRef) {
			boolean found = false;
			for (ResourceRef item : resourceRef) {
				if(anon.getResRefName().equalsIgnoreCase(item.getResRefName())){
					found = true;
					break;
				}
			}
			if(!found){
				listResourceRef.add(anon);
			}
		}
        
        this.resourceRef.addAll(listResourceRef);
        
        
        List<SecurityRole> listSecurityRole = new ArrayList<SecurityRole>();
		for (SecurityRole anon : webAppAnon.securityRole) {
			boolean found = false;
			for (SecurityRole item : securityRole) {
				if(anon.getRoleName().equalsIgnoreCase(item.getRoleName())){
					found = true;
					break;
				}
			}
			if(!found){
				listSecurityRole.add(anon);
			}
		}
        
        this.securityRole.addAll(listSecurityRole);
        
        List<ServiceRef> listServiceRef = new ArrayList<ServiceRef>();
		for (ServiceRef anon : webAppAnon.serviceRef) {
			boolean found = false;
			for (ServiceRef item : serviceRef) {
				if(anon.getServiceRefName().equalsIgnoreCase(item.getServiceRefName())){
					found = true;
					break;
				}
			}
			if(!found){
				listServiceRef.add(anon);
			}
		}
        
        this.serviceRef.addAll(listServiceRef);
        
        List<Servlet> listServlet = new ArrayList<Servlet>();
		for (Servlet anon : webAppAnon.servlet) {
			boolean found = false;
			for (Servlet item : servlet) {
				if(anon.getServletName().equalsIgnoreCase(item.getServletName())){
					found = true;
					break;
				}
			}
			if(!found){
				listServlet.add(anon);
			}
		}
		
        this.servlet.addAll(listServlet);
        
        return this;
    }
}
