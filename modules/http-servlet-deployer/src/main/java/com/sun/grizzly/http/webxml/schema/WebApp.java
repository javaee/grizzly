/**
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER. *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved. *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */
package com.sun.grizzly.http.webxml.schema;

import java.util.List;

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

	public List<Icon> icon;
	public List<String> displayName;
	public List<String> description;
	public boolean distributable;
	public List<ContextParam> contextParam;
	public List<Filter> filter;
	public List<FilterMapping> filterMapping;
	public List<Listener> listener;
	public List<Servlet> servlet;
	public List<ServletMapping> servletMapping;
	public List<SessionConfig> sessionConfig;
	public List<MimeMapping> mimeMapping;
	public List<WelcomeFileList> welcomeFileList;
	public List<ErrorPage> errorPage;
	public List<Taglib> taglib;
	public List<ResourceEnvRef> resourceEnvRef;
	public List<ResourceRef> resourceRef;
	public List<SecurityConstraint> securityConstraint;
	public List<LoginConfig> loginConfig;
	public List<SecurityRole> securityRole;
	public List<EnvEntry> envEntry;
	public List<EjbRef> ejbRef;
	public List<EjbLocalRef> ejbLocalRef;
	public List<JspConfig> jspConfig;
	public List<ServiceRef> serviceRef;
	public List<MessageDestination> messageDestination;
	public List<MessageDestinationRef> messageDestinationRef;
	public List<PersistenceContextRef> persistenceContextRef;
	public List<PersistenceUnitRef> persistenceUnitRef;
	public List<LifecycleCallback> postConstruct;
	public List<LifecycleCallback> preDestroy;
	public List<LocaleEncodingMappingList> localeEncodingMappingList;

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
		this.icon = icon;
	}
	
	/**
	 * &lt;display-name&gt;name&lt;/display-name&gt; 
	 */
	public List<String> getDisplayName() {
		return displayName;
	}

	public void setDisplayName(List<String> displayName) {
		this.displayName = displayName;
	}

	/**
	 * &lt;description&gt;description&lt;/description&gt;
	 */
	public List<String> getDescription() {
		return description;
	}

	public void setDescription(List<String> description) {
		this.description = description;
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
		this.servlet = servlet;
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
		this.filter = filter;
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
		this.contextParam = contextParam;
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
		this.ejbLocalRef = ejbLocalRef;
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
		this.ejbRef = ejbRef;
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
		this.envEntry = envEntry;
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
		this.errorPage = errorPage;
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
		this.filterMapping = filterMapping;
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
		this.listener = listener;
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
		this.loginConfig = loginConfig;
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
		this.mimeMapping = mimeMapping;
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
		this.resourceEnvRef = resourceEnvRef;
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
		this.securityConstraint = securityConstraint;
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
		this.servletMapping = servletMapping;
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
		this.sessionConfig = sessionConfig;
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
		this.taglib = taglib;
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
		this.securityRole = securityRole;
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
		this.resourceRef = resourceRef;
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
		this.welcomeFileList = welcomeFileList;
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
		this.jspConfig = jspConfig;
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
		this.serviceRef = serviceRef;
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
		this.messageDestination = messageDestination;
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
		this.messageDestinationRef = messageDestinationRef;
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
		this.persistenceContextRef = persistenceContextRef;
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
		this.persistenceUnitRef = persistenceUnitRef;
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
		this.postConstruct = postConstruct;
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
		this.preDestroy = preDestroy;
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
		this.localeEncodingMappingList = localeEncodingMappingList;
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

			for (LifecycleCallback item : list) {
				buffer.append(item).append("\n");
			}
		}

		if (preDestroy != null) {
			List<LifecycleCallback> list = preDestroy;

			for (LifecycleCallback item : list) {
				buffer.append(item).append("\n");
			}
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

}
