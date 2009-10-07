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
	 * <icon>
	 *	<small-icon>token</small-icon>
	 *	<large-icon>token</large-icon>
	 * </icon>
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
	 * <display-name>name</display-name> 
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
	 * <description>description</description>
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
	 * <distributable></distributable>
	 */
	public boolean getDistributable() {
		return distributable;
	}

	public void setDistributable(boolean distributable) {
		this.distributable = distributable;
	}

	/**
	 * <servlet>
     *  <servlet-name>Hello</servlet-name>
     *	<servlet-class>ca.sebastiendionne.HelloWorld</servlet-class>
     *	<load-on-startup>1</load-on-startup>
  	 * </servlet>
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
	 * <filter>
     *   <filter-name>filter</filter-name>
     *   <filter-class>ca.sebastiendionne.TestFilter</filter-class>
     *   <init-param>
     *    <param-name>paramName</param-name>
     *    <param-value>paramValue</param-value>
     *   </init-param>
     *  </filter>
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
	 * <context-param>
	 *	<description>description</description>
	 *	<param-name>token</param-name>
	 *  <param-value>value</param-value>
	 * </context-param>
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
	 * <ejb-local-ref>
	 *	<description>description</description>
	 *	<ejb-ref-name>token</ejb-ref-name>
	 *	<ejb-ref-type>Session</ejb-ref-type>
	 *	<local-home>token</local-home>
	 *	<local>token</local>
	 *	<ejb-link>token</ejb-link>
	 *	<mapped-name>string</mapped-name>
	 *	<injection-target>
	 *		<injection-target-class>token</injection-target-class>
	 *		<injection-target-name>$</injection-target-name>
	 *	</injection-target>
	 * </ejb-local-ref>
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
	 * <ejb-ref>
	 *  <description>description</description>
	 *	<ejb-ref-name>token</ejb-ref-name>
	 *	<ejb-ref-type>Session</ejb-ref-type>
	 *	<home>token</home>
	 *	<remote>token</remote>
	 *	<ejb-link>token</ejb-link>
	 *	<mapped-name>string</mapped-name>
	 *	<injection-target>
	 *		<injection-target-class>token</injection-target-class>
	 *		<injection-target-name>$</injection-target-name>
	 *	</injection-target>
	 * </ejb-ref>
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
	 * <env-entry>
	 *	<description>description</description>
	 *	<env-entry-name>token</env-entry-name>
	 *	<env-entry-type>java.lang.Double</env-entry-type>
	 *	<env-entry-value>string</env-entry-value>
	 *	<mapped-name>string</mapped-name>
	 *	<injection-target>
	 *		<injection-target-class>token</injection-target-class>
	 *		<injection-target-name>$</injection-target-name>
	 *	</injection-target>
	 * </env-entry>
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
	 * <error-page>
	 *	<error-code>404</error-code>
	 *	<exception-type>token</exception-type>
	 *	<location>/</location>
	 * </error-page>
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
	 * <filter-mapping>
	 *	<filter-name/>
	 *	<url-pattern>string</url-pattern>
	 *	<servlet-name/>
	 *	<url-pattern>string</url-pattern>
	 *	<servlet-name/>
	 *	<url-pattern>string</url-pattern>
	 *	<servlet-name/>
	 *	<dispatcher>ERROR</dispatcher>
	 * </filter-mapping>
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
	 * <listener>
	 *	<description>description</description>
	 *	<display-name>token</display-name>
	 *	<icon>
	 *		<small-icon>token</small-icon>
	 *		<large-icon>token</large-icon>
	 *	</icon>
	 *	<listener-class>token</listener-class>
	 * </listener>
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
	 * <login-config>
	 *	<auth-method>token</auth-method>
	 *	<realm-name>token</realm-name>
	 *	<form-login-config>
	 *		<form-login-page>/</form-login-page>
	 *		<form-error-page>/</form-error-page>
	 *	</form-login-config>
	 * </login-config>
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
	 * <mime-mapping>
	 *	<extension>token</extension>
	 *	<mime-type>!/!</mime-type>
	 * </mime-mapping>
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
	 * <resource-env-ref>
	 *	<description>description</description>
	 *	<resource-env-ref-name>token</resource-env-ref-name>
	 *	<resource-env-ref-type>token</resource-env-ref-type>
	 *	<mapped-name>string</mapped-name>
	 *	<injection-target>
	 *		<injection-target-class>token</injection-target-class>
	 *		<injection-target-name>$</injection-target-name>
	 *	</injection-target>
	 * </resource-env-ref>
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
	 * <security-constraint>
	 *	<display-name>displayname</display-name>
	 *	<web-resource-collection>
	 *		<web-resource-name>token</web-resource-name>
	 *		<description>description</description>
	 *		<url-pattern>string</url-pattern>
	 *		<http-method>t</http-method>
	 *	</web-resource-collection>
	 *	<user-data-constraint>
	 *		<description>description</description>
	 *		<transport-guarantee>CONFIDENTIAL</transport-guarantee>
	 *	</user-data-constraint>
	 * </security-constraint>
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
	 * <servlet-mapping>
	 *	<servlet-name>servletname</servlet-name>
	 *	<url-pattern>/*.jsp</url-pattern>
	 * </servlet-mapping>
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
	 * <session-config>
	 *	<session-timeout>120</session-timeout>
	 *	<cookie-config>
	 *		<name>name</name>
	 *		<domain>domain</domain>
	 *		<path>path</path>
	 *		<comment>comment</comment>
	 *		<http-only>true</http-only>
	 *		<secure>true</secure>
	 *	</cookie-config>
	 *	<tracking-mode>SSL</tracking-mode>
	 * </session-config>
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
	 * <taglib>
	 *		<taglib-uri>uri</taglib-uri>
	 *		<taglib-location>location</taglib-location>
	 *	</taglib>
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
	 * <security-role>
	 *	<description>description</description>
	 *	<role-name>token</role-name>
	 * </security-role>
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
	 * <resource-ref>
	 *	<description>description</description>
	 *	<res-ref-name>token</res-ref-name>
	 *	<res-type>token</res-type>
	 *	<res-auth>Container</res-auth>
	 *	<res-sharing-scope>Unshareable</res-sharing-scope>
	 *	<mapped-name>string</mapped-name>
	 *	<injection-target>
	 *		<injection-target-class>token</injection-target-class>
	 *		<injection-target-name>$</injection-target-name>
	 *	</injection-target>
	 * </resource-ref>
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
	 * <welcome-file-list>
	 *	<welcome-file>index.jsp</welcome-file>
	 *	<welcome-file>index.html</welcome-file>
	 * </welcome-file-list>
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
	 * <jsp-config>
	 *	<taglib>
	 *		<taglib-uri>uri</taglib-uri>
	 *		<taglib-location>localtion</taglib-location>
	 *	</taglib>
	 *	<jsp-property-group>
	 *		<description>description</description>
	 *		<display-name>displayname</display-name>
	 *		<icon>
	 *			<small-icon>token</small-icon>
	 *			<large-icon>token</large-icon>
	 *		</icon>
	 *		<url-pattern>string</url-pattern>
	 *		<el-ignored>true</el-ignored>
	 *		<page-encoding>token</page-encoding>
	 *		<scripting-invalid>true</scripting-invalid>
	 *		<is-xml>true</is-xml>
	 *		<include-prelude>token</include-prelude>
	 *		<include-coda>token</include-coda>
	 *		<deferred-syntax-allowed-as-literal>true</deferred-syntax-allowed-as-literal>
	 *		<trim-directive-whitespaces>true</trim-directive-whitespaces>
	 *		<default-content-type>token</default-content-type>
	 *		<buffer>token</buffer>
	 *		<error-on-undeclared-namespace>true</error-on-undeclared-namespace>
	 *	</jsp-property-group>
	 * </jsp-config>
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
	 * <service-ref>
	 *	<description>description</description>
	 *	<display-name>token</display-name>
	 *	<icon>
	 *		<small-icon>token</small-icon>
	 *		<large-icon>token</large-icon>
	 *	</icon>
	 *	<service-ref-name>token</service-ref-name>
	 *	<service-interface>token</service-interface>
	 *	<service-ref-type>token</service-ref-type>
	 *	<wsdl-file> URI</wsdl-file>
	 *	<jaxrpc-mapping-file>token</jaxrpc-mapping-file>
	 *	<service-qname>pref:name</service-qname>
	 *	<port-component-ref>
	 *		<service-endpoint-interface>token</service-endpoint-interface>
	 *		<enable-mtom>true</enable-mtom>
	 *		<port-component-link>token</port-component-link>
	 *	</port-component-ref>
	 *	<handler>
	 *		<description>description</description>
	 *		<display-name>token</display-name>
	 *		<icon>
	 *			<small-icon>token</small-icon>
	 *			<large-icon>token</large-icon>
	 *		</icon>
	 *		<handler-name>token</handler-name>
	 *		<handler-class>token</handler-class>
	 *		<init-param>
	 *			<description>string</description>
	 *			<param-name>token</param-name>
	 *			<param-value>string</param-value>
	 *		</init-param>
	 *		<soap-header>pref:name</soap-header>
	 *		<soap-role>token</soap-role>
	 *		<port-name>token</port-name>
	 *	</handler>
	 *	<handler-chains>
	 *		<handler-chain>
	 *			<service-name-pattern>*</service-name-pattern>
	 *			<port-name-pattern>*</port-name-pattern>
	 *			<protocol-bindings/>
	 *			<handler>
	 *				<description>string</description>
	 *				<display-name>token</display-name>
	 *				<icon>
	 *					<small-icon>token</small-icon>
	 *					<large-icon>token</large-icon>
	 *				</icon>
	 *				<handler-name>token</handler-name>
	 *				<handler-class>token</handler-class>
	 *				<init-param>
	 *					<description>string</description>
	 *					<param-name>token</param-name>
	 *					<param-value>string</param-value>
	 *				</init-param>
	 *				<soap-header>pref:name</soap-header>
	 *				<soap-role>token</soap-role>
	 *				<port-name>token</port-name>
	 *			</handler>
	 *		</handler-chain>
	 *	</handler-chains>
	 *	<mapped-name>string</mapped-name>
	 *	<injection-target>
	 *		<injection-target-class>token</injection-target-class>
	 *		<injection-target-name>$</injection-target-name>
	 *	</injection-target>
	 * </service-ref>
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
	 * <message-destination>
	 *	<description>string</description>
	 *	<display-name>token</display-name>
	 *	<icon>
	 *		<small-icon>token</small-icon>
	 *		<large-icon>token</large-icon>
	 *	</icon>
	 *	<message-destination-name>token</message-destination-name>
	 *	<mapped-name>string</mapped-name>
	 * </message-destination>
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
	 * <message-destination-ref>
	 *	<description>string</description>
	 *	<message-destination-ref-name>token</message-destination-ref-name>
	 *	<message-destination-type>token</message-destination-type>
	 *	<message-destination-usage>ConsumesProduces</message-destination-usage>
	 *	<message-destination-link>token</message-destination-link>
	 *	<mapped-name>string</mapped-name>
	 *	<injection-target>
	 *		<injection-target-class>token</injection-target-class>
	 *		<injection-target-name>$</injection-target-name>
	 *	</injection-target>
	 * </message-destination-ref>
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
	 * <persistence-context-ref>
	 *	<description>string</description>
	 *	<persistence-context-ref-name>token</persistence-context-ref-name>
	 *	<persistence-unit-name>token</persistence-unit-name>
	 *	<persistence-context-type>Extended</persistence-context-type>
	 *	<persistence-property>
	 *		<name>string</name>
	 *		<value>string</value>
	 *	</persistence-property>
	 *	<mapped-name>string</mapped-name>
	 *	<injection-target>
	 *		<injection-target-class>token</injection-target-class>
	 *		<injection-target-name>$</injection-target-name>
	 *	</injection-target>
	 * </persistence-context-ref>
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
	 * <persistence-unit-ref>
	 *	<description>string</description>
	 *	<persistence-unit-ref-name>token</persistence-unit-ref-name>
	 *	<persistence-unit-name>token</persistence-unit-name>
	 *	<mapped-name>string</mapped-name>
	 *	<injection-target>
	 *		<injection-target-class>token</injection-target-class>
	 *		<injection-target-name>$</injection-target-name>
	 *	</injection-target>
	 * </persistence-unit-ref>
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
	 * <post-construct>
	 *	<lifecycle-callback-class>token</lifecycle-callback-class>
	 *	<lifecycle-callback-method>$</lifecycle-callback-method>
	 * </post-construct>
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
	 * <pre-destroy>
	 *	<lifecycle-callback-class>token</lifecycle-callback-class>
	 *	<lifecycle-callback-method>$</lifecycle-callback-method>
	 * </pre-destroy>
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
	 * <locale-encoding-mapping-list>
	 *	<locale-encoding-mapping>
	 *		<locale>st_ri</locale>
	 *		<encoding>s</encoding>
	 *	</locale-encoding-mapping>
	 * </locale-encoding-mapping-list>
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
