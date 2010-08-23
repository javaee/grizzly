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

package com.sun.grizzly.http.webxml.parser;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;

import com.sun.grizzly.http.webxml.schema.version_2_3.ServletName;
import com.sun.grizzly.http.webxml.schema.version_2_3.UrlPattern;
import com.sun.grizzly.http.webxml.schema.version_3_0.AuthConstraintType;
import com.sun.grizzly.http.webxml.schema.version_3_0.CookieConfigType;
import com.sun.grizzly.http.webxml.schema.version_3_0.DescriptionType;
import com.sun.grizzly.http.webxml.schema.version_3_0.DispatcherType;
import com.sun.grizzly.http.webxml.schema.version_3_0.DisplayNameType;
import com.sun.grizzly.http.webxml.schema.version_3_0.EjbLocalRefType;
import com.sun.grizzly.http.webxml.schema.version_3_0.EjbRefType;
import com.sun.grizzly.http.webxml.schema.version_3_0.EnvEntryType;
import com.sun.grizzly.http.webxml.schema.version_3_0.ErrorPageType;
import com.sun.grizzly.http.webxml.schema.version_3_0.FilterMappingType;
import com.sun.grizzly.http.webxml.schema.version_3_0.FilterType;
import com.sun.grizzly.http.webxml.schema.version_3_0.IconType;
import com.sun.grizzly.http.webxml.schema.version_3_0.InjectionTargetType;
import com.sun.grizzly.http.webxml.schema.version_3_0.JspConfigType;
import com.sun.grizzly.http.webxml.schema.version_3_0.JspPropertyGroupType;
import com.sun.grizzly.http.webxml.schema.version_3_0.LifecycleCallbackType;
import com.sun.grizzly.http.webxml.schema.version_3_0.ListenerType;
import com.sun.grizzly.http.webxml.schema.version_3_0.LocaleEncodingMappingListType;
import com.sun.grizzly.http.webxml.schema.version_3_0.LocaleEncodingMappingType;
import com.sun.grizzly.http.webxml.schema.version_3_0.LoginConfigType;
import com.sun.grizzly.http.webxml.schema.version_3_0.MessageDestinationRefType;
import com.sun.grizzly.http.webxml.schema.version_3_0.MessageDestinationType;
import com.sun.grizzly.http.webxml.schema.version_3_0.MimeMappingType;
import com.sun.grizzly.http.webxml.schema.version_3_0.ParamValueType;
import com.sun.grizzly.http.webxml.schema.version_3_0.PathType;
import com.sun.grizzly.http.webxml.schema.version_3_0.PersistenceContextRefType;
import com.sun.grizzly.http.webxml.schema.version_3_0.PersistenceUnitRefType;
import com.sun.grizzly.http.webxml.schema.version_3_0.PortComponentRefType;
import com.sun.grizzly.http.webxml.schema.version_3_0.PropertyType;
import com.sun.grizzly.http.webxml.schema.version_3_0.ResourceEnvRefType;
import com.sun.grizzly.http.webxml.schema.version_3_0.ResourceRefType;
import com.sun.grizzly.http.webxml.schema.version_3_0.RoleNameType;
import com.sun.grizzly.http.webxml.schema.version_3_0.RunAsType;
import com.sun.grizzly.http.webxml.schema.version_3_0.SecurityConstraintType;
import com.sun.grizzly.http.webxml.schema.version_3_0.SecurityRoleRefType;
import com.sun.grizzly.http.webxml.schema.version_3_0.SecurityRoleType;
import com.sun.grizzly.http.webxml.schema.version_3_0.ServiceRefHandlerChainType;
import com.sun.grizzly.http.webxml.schema.version_3_0.ServiceRefHandlerChainsType;
import com.sun.grizzly.http.webxml.schema.version_3_0.ServiceRefHandlerType;
import com.sun.grizzly.http.webxml.schema.version_3_0.ServiceRefType;
import com.sun.grizzly.http.webxml.schema.version_3_0.ServletMappingType;
import com.sun.grizzly.http.webxml.schema.version_3_0.ServletType;
import com.sun.grizzly.http.webxml.schema.version_3_0.SessionConfigType;
import com.sun.grizzly.http.webxml.schema.version_3_0.TaglibType;
import com.sun.grizzly.http.webxml.schema.version_3_0.TrackingModeType;
import com.sun.grizzly.http.webxml.schema.version_3_0.UrlPatternType;
import com.sun.grizzly.http.webxml.schema.version_3_0.WebAppType;
import com.sun.grizzly.http.webxml.schema.version_3_0.WebResourceCollectionType;
import com.sun.grizzly.http.webxml.schema.version_3_0.WelcomeFileListType;
import com.sun.grizzly.http.webxml.schema.version_3_0.XsdQNameType;



public class JAXBWebXml_3_0Parser implements IJAXBWebXmlParser {
	
	Map<String, List<JAXBElement<?>>> itemMap = new HashMap<String, List<JAXBElement<?>>>();
	
	@SuppressWarnings("unchecked")
	public com.sun.grizzly.http.webxml.schema.WebApp parse(String webxml) throws Exception {
		
		JAXBContext jc = JAXBContext.newInstance("com.sun.grizzly.http.webxml.schema.version_3_0");
        
        // create an Unmarshaller
        Unmarshaller u = jc.createUnmarshaller();
        
        JAXBElement root = (JAXBElement) u.unmarshal(new FileInputStream(webxml));
        
        com.sun.grizzly.http.webxml.schema.WebApp webApp = populate((WebAppType)root.getValue());
        
        return webApp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.Servlet> populateServlet(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("ServletType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("ServletType");
		
		List<com.sun.grizzly.http.webxml.schema.Servlet> servletList = new ArrayList<com.sun.grizzly.http.webxml.schema.Servlet>();
		
		for (JAXBElement obj : list) {
			ServletType servlet = (ServletType)obj.getValue();
			
			com.sun.grizzly.http.webxml.schema.Servlet servletTmp = new com.sun.grizzly.http.webxml.schema.Servlet();
			
			if(servlet.getIcon()!=null && servlet.getIcon().size()>0){
				servletTmp.setIcon(populateIcon(servlet.getIcon()));
			}
			if(servlet.getDescription()!=null && servlet.getDescription().size()>0){
				servletTmp.setDescription(populateDescription(servlet.getDescription()));
			}
			if(servlet.getDisplayName()!=null && servlet.getDisplayName().size()>0){
				servletTmp.setDisplayName(populateDisplayName(servlet.getDisplayName()));
			}
			if(servlet.getServletName()!=null){
				servletTmp.setServletName(servlet.getServletName().getValue());
			}
			if(servlet.getLoadOnStartup()!=null){
				servletTmp.setLoadOnStartup(servlet.getLoadOnStartup());
			}
			if(servlet.getJspFile()!=null){
				servletTmp.setJspFile(servlet.getJspFile().getValue());
			}
			if(servlet.getServletClass()!=null){
				servletTmp.setServletClass(servlet.getServletClass().getValue());
			}
			if(servlet.getAsyncSupported()!=null){
				servletTmp.setAsyncSupported(true);
			}
			if(servlet.getAsyncTimeout()!=null){
				servletTmp.setAsyncTimeout(servlet.getAsyncTimeout().getValue().toString());
			}
			if(servlet.getRunAs()!=null){
				servletTmp.setRunAs(getRunAs(servlet.getRunAs()));
			}
			
			
			List<ParamValueType> initParams = servlet.getInitParam();
			
			if(initParams!=null){
				List<com.sun.grizzly.http.webxml.schema.InitParam> initParamsTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.InitParam>(initParams.size());
				for (ParamValueType initParam : initParams) {
					initParamsTmp.add(getInitParam(initParam));
				}
				
				servletTmp.setInitParam(initParamsTmp);
			}
			
			List<SecurityRoleRefType> securityRoleRefList = servlet.getSecurityRoleRef();
			
			if(securityRoleRefList!=null){
				List<com.sun.grizzly.http.webxml.schema.SecurityRoleRef> securityRoleRefTmpList = new ArrayList<com.sun.grizzly.http.webxml.schema.SecurityRoleRef>(securityRoleRefList.size());
				for (SecurityRoleRefType securityRoleRef : securityRoleRefList) {
					securityRoleRefTmpList.add(getSecurityRoleRef(securityRoleRef));
				}
				
				servletTmp.setSecurityRoleRef(securityRoleRefTmpList);
			}
		
			servletList.add(servletTmp);
		}
		
		return servletList;
	}
	
	@SuppressWarnings("unchecked")
	protected Map<String, List<JAXBElement<?>>> getItemMap(List<JAXBElement<?>> itemList) throws Exception {
		// need to find something nicer
		Map<String, List<JAXBElement<?>>> itemMap = null;

		if (itemList != null) {
			itemMap = new HashMap<String, List<JAXBElement<?>>>();
			// convert it to a Map, will be easier to retrieve values
			for (JAXBElement element : itemList) {
				List<JAXBElement<?>> list = null;
				String key = element.getValue().getClass().getSimpleName();
				if (itemMap.containsKey(key)) {
					list = itemMap.get(key);
				} else {
					list = new ArrayList<JAXBElement<?>>();
					itemMap.put(key, list);
				}
				list.add(element);
			}
		} else {
			// error handling when list is null ...
			throw new Exception("invalid");
		}

		return itemMap;
	}
	
	private com.sun.grizzly.http.webxml.schema.WebApp populate(WebAppType root) throws Exception {
		
		com.sun.grizzly.http.webxml.schema.WebApp webApp = new com.sun.grizzly.http.webxml.schema.WebApp();
		
		List<JAXBElement<?>> itemList = root.getDescriptionAndDisplayNameAndIcon();
		
		// extract the items from the web.xml
		Map<String, List<JAXBElement<?>>> itemMap = getItemMap(itemList);
		
		if (itemMap == null || itemMap.size()==0) {
			throw new Exception("invalid");
		}
		
		// Distributable
		if(itemMap.containsKey("EmptyType")){
			webApp.setDistributable(true);
		}
		
		// metadata-complete
		if(root.isMetadataComplete()){
			webApp.setMetadataComplete(true);
		}
		
		webApp.setDisplayName(populateDisplayName(itemMap));
		webApp.setDescription(populateDescription(itemMap));
		webApp.setIcon(populateIcon(itemMap));
		webApp.setServlet(populateServlet(itemMap));
		webApp.setServletMapping(populateServletMapping(itemMap));
		webApp.setFilter(populateFilter(itemMap));
		webApp.setFilterMapping(populateFilterMapping(itemMap));
		webApp.setContextParam(populateContextParam(itemMap));
		webApp.setEjbLocalRef(populateEjbLocalRef(itemMap));
		webApp.setEjbRef(populateEjbRef(itemMap));
		webApp.setEnvEntry(populateEnvEntry(itemMap));
		webApp.setErrorPage(populateErrorPage(itemMap));
		webApp.setJspConfig(populateJspConfig(itemMap));
		webApp.setListener(populateListener(itemMap));
		webApp.setLoginConfig(populateLoginConfig(itemMap));
		webApp.setMimeMapping(populateMimeMapping(itemMap));
		webApp.setResourceRef(populateResourceRef(itemMap));
		webApp.setResourceEnvRef(populateResourceEnvRef(itemMap));
		webApp.setServiceRef(populateServiceRef(itemMap));
		webApp.setSecurityConstraint(populateSecurityConstraint(itemMap));
		webApp.setSecurityRole(populateSecurityRole(itemMap));
		webApp.setSessionConfig(populateSessionConfig(itemMap));
		webApp.setWelcomeFileList(populateWelcomeFileList(itemMap));
		webApp.setMessageDestination(populateMessageDestination(itemMap));
		webApp.setMessageDestinationRef(populateMessageDestinationRef(itemMap));
		webApp.setPersistenceContextRef(populatePersistenceContextRef(itemMap));
		webApp.setPersistenceUnitRef(populatePersistenceUnitRef(itemMap));
		
		// pre-construct and pre-destroy
		webApp.setPreDestroy(populatePreDestroyList(itemMap));
		webApp.setPostConstruct(populatePostConstructList(itemMap));
		webApp.setLocaleEncodingMappingList(populateLocaleEncodingMappingList(itemMap));
		
		return webApp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.LifecycleCallback> populatePreDestroyList(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("LifecycleCallbackType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("LifecycleCallbackType");
		
		List<com.sun.grizzly.http.webxml.schema.LifecycleCallback> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.LifecycleCallback>(list.size());
		for (JAXBElement obj : list) {
			if(obj.getName().getLocalPart().equalsIgnoreCase("pre-destroy")){
				LifecycleCallbackType item = (LifecycleCallbackType)obj.getValue();
				listTmp.add(getLifecycleCallback(item));
			}
		}
		
		return listTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.LifecycleCallback> populatePostConstructList(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("LifecycleCallbackType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("LifecycleCallbackType");
		
		List<com.sun.grizzly.http.webxml.schema.LifecycleCallback> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.LifecycleCallback>(list.size());
		for (JAXBElement obj : list) {
			if(obj.getName().getLocalPart().equalsIgnoreCase("post-construct")){
				LifecycleCallbackType item = (LifecycleCallbackType)obj.getValue();
				listTmp.add(getLifecycleCallback(item));
			}
		}
		
		return listTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.LifecycleCallback getLifecycleCallback(LifecycleCallbackType lifecycleCallback){
		
		if(lifecycleCallback==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.LifecycleCallback lifecycleCallbackTmp = new com.sun.grizzly.http.webxml.schema.LifecycleCallback();
		
		if(lifecycleCallback.getLifecycleCallbackClass()!=null){
			lifecycleCallbackTmp.setLifecycleCallbackClass(lifecycleCallback.getLifecycleCallbackClass().getValue());
		}
		if(lifecycleCallback.getLifecycleCallbackMethod()!=null){
			lifecycleCallbackTmp.setLifecycleCallbackMethod(lifecycleCallback.getLifecycleCallbackMethod().getValue());
		}
				
		return lifecycleCallbackTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.LocaleEncodingMappingList> populateLocaleEncodingMappingList(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("LocaleEncodingMappingListType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("LocaleEncodingMappingListType");
		
		List<com.sun.grizzly.http.webxml.schema.LocaleEncodingMappingList> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.LocaleEncodingMappingList>(list.size());
		for (JAXBElement obj : list) {
			LocaleEncodingMappingListType item = (LocaleEncodingMappingListType)obj.getValue();
			listTmp.add(getLocaleEncodingMappingList(item));
		}
		
		return listTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.LocaleEncodingMappingList getLocaleEncodingMappingList(LocaleEncodingMappingListType localeEncodingMappingList){
		
		if(localeEncodingMappingList==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.LocaleEncodingMappingList localeEncodingMappingListTmp = new com.sun.grizzly.http.webxml.schema.LocaleEncodingMappingList();
		
		if(localeEncodingMappingList.getLocaleEncodingMapping()!=null && localeEncodingMappingList.getLocaleEncodingMapping().size()>0){
			localeEncodingMappingListTmp.setLocaleEncodingMapping(populateLocaleEncodingMapping(localeEncodingMappingList.getLocaleEncodingMapping()));
		}
				
		return localeEncodingMappingListTmp;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.LocaleEncodingMapping> populateLocaleEncodingMapping(List<LocaleEncodingMappingType> list){
		
		if(list==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.LocaleEncodingMapping> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.LocaleEncodingMapping>(list.size());
		for (LocaleEncodingMappingType obj : list) {
			LocaleEncodingMappingType item = (LocaleEncodingMappingType)obj;
			
			com.sun.grizzly.http.webxml.schema.LocaleEncodingMapping mapping = new com.sun.grizzly.http.webxml.schema.LocaleEncodingMapping();
			
			mapping.setLocale(item.getLocale());
			mapping.setEncoding(item.getEncoding());
			
			listTmp.add(mapping);
		}
		
		return listTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.PersistenceUnitRef> populatePersistenceUnitRef(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("PersistenceUnitRefType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("PersistenceUnitRefType");
		
		List<com.sun.grizzly.http.webxml.schema.PersistenceUnitRef> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.PersistenceUnitRef>(list.size());
		for (JAXBElement obj : list) {
			PersistenceUnitRefType item = (PersistenceUnitRefType)obj.getValue();
			listTmp.add(getPersistenceUnitRef(item));
		}
		
		return listTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.PersistenceUnitRef getPersistenceUnitRef(PersistenceUnitRefType persistenceUnitRef){
		
		if(persistenceUnitRef==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.PersistenceUnitRef persistenceUnitRefTmp = new com.sun.grizzly.http.webxml.schema.PersistenceUnitRef();
		
		if(persistenceUnitRef.getDescription()!=null && persistenceUnitRef.getDescription().size()>0){
			persistenceUnitRefTmp.setDescription(populateDescription(persistenceUnitRef.getDescription()));
		}
		if(persistenceUnitRef.getPersistenceUnitName()!=null){
			persistenceUnitRefTmp.setPersistenceUnitName(persistenceUnitRef.getPersistenceUnitName().getValue());
		}
		if(persistenceUnitRef.getPersistenceUnitRefName()!=null){
			persistenceUnitRefTmp.setPersistenceUnitRefName(persistenceUnitRef.getPersistenceUnitRefName().getValue());
		}
		if(persistenceUnitRef.getMappedName()!=null){
			persistenceUnitRefTmp.setMappedName(persistenceUnitRef.getMappedName().getValue());
		}
		if(persistenceUnitRef.getInjectionTarget()!=null && persistenceUnitRef.getInjectionTarget().size()>0){
			persistenceUnitRefTmp.setInjectionTarget(populateInjectionTarget(persistenceUnitRef.getInjectionTarget()));
		}
		
		return persistenceUnitRefTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.PersistenceContextRef> populatePersistenceContextRef(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("PersistenceContextRefType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("PersistenceContextRefType");
		
		List<com.sun.grizzly.http.webxml.schema.PersistenceContextRef> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.PersistenceContextRef>(list.size());
		for (JAXBElement obj : list) {
			PersistenceContextRefType item = (PersistenceContextRefType)obj.getValue();
			listTmp.add(getPersistenceContextRef(item));
		}
		
		return listTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.PersistenceContextRef getPersistenceContextRef(PersistenceContextRefType persistenceContextRef){
		
		if(persistenceContextRef==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.PersistenceContextRef persistenceContextRefTmp = new com.sun.grizzly.http.webxml.schema.PersistenceContextRef();
		
		if(persistenceContextRef.getDescription()!=null && persistenceContextRef.getDescription().size()>0){
			persistenceContextRefTmp.setDescription(populateDescription(persistenceContextRef.getDescription()));
		}
		if(persistenceContextRef.getPersistenceContextRefName()!=null){
			persistenceContextRefTmp.setPersistenceContextRefName(persistenceContextRef.getPersistenceContextRefName().getValue());
		}
		if(persistenceContextRef.getPersistenceContextType()!=null){
			persistenceContextRefTmp.setPersistenceContextType(persistenceContextRef.getPersistenceContextType().getValue());
		}
		if(persistenceContextRef.getPersistenceUnitName()!=null){
			persistenceContextRefTmp.setPersistenceUnitName(persistenceContextRef.getPersistenceUnitName().getValue());
		}
		if(persistenceContextRef.getPersistenceProperty()!=null  && persistenceContextRef.getPersistenceProperty().size()>0){
			persistenceContextRefTmp.setPersistenceProperty(populatePersistenceProperty(persistenceContextRef.getPersistenceProperty()));
		}
		if(persistenceContextRef.getMappedName()!=null){
			persistenceContextRefTmp.setMappedName(persistenceContextRef.getMappedName().getValue());
		}
		if(persistenceContextRef.getInjectionTarget()!=null && persistenceContextRef.getInjectionTarget().size()>0){
			persistenceContextRefTmp.setInjectionTarget(populateInjectionTarget(persistenceContextRef.getInjectionTarget()));
		}
		
		return persistenceContextRefTmp;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.Property> populatePersistenceProperty(List<PropertyType> list){
		
		if(list==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.Property> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.Property>(list.size());
		for (PropertyType obj : list) {
			PropertyType item = (PropertyType)obj;
			
			com.sun.grizzly.http.webxml.schema.Property property = new com.sun.grizzly.http.webxml.schema.Property();
			
			property.setName(item.getName().getValue());
			property.setValue(item.getValue().getValue());
			
			listTmp.add(property);
		}
		
		return listTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.MessageDestinationRef> populateMessageDestinationRef(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("MessageDestinationRefType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("MessageDestinationRefType");
		
		List<com.sun.grizzly.http.webxml.schema.MessageDestinationRef> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.MessageDestinationRef>(list.size());
		for (JAXBElement obj : list) {
			MessageDestinationRefType item = (MessageDestinationRefType)obj.getValue();
			listTmp.add(getMessageDestinationRef(item));
		}
		
		return listTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.MessageDestinationRef getMessageDestinationRef(MessageDestinationRefType messageDestinationRef){
		
		if(messageDestinationRef==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.MessageDestinationRef messageDestinationRefTmp = new com.sun.grizzly.http.webxml.schema.MessageDestinationRef();
		
		if(messageDestinationRef.getDescription()!=null && messageDestinationRef.getDescription().size()>0){
			messageDestinationRefTmp.setDescription(populateDescription(messageDestinationRef.getDescription()));
		}
		if(messageDestinationRef.getMessageDestinationLink()!=null){
			messageDestinationRefTmp.setMessageDestinationLink(messageDestinationRef.getMessageDestinationLink().getValue());
		}
		if(messageDestinationRef.getMessageDestinationRefName()!=null){
			messageDestinationRefTmp.setMessageDestinationRefName(messageDestinationRef.getMessageDestinationRefName().getValue());
		}
		if(messageDestinationRef.getMessageDestinationType()!=null){
			messageDestinationRefTmp.setMessageDestinationType(messageDestinationRef.getMessageDestinationType().getValue());
		}
		if(messageDestinationRef.getMappedName()!=null){
			messageDestinationRefTmp.setMappedName(messageDestinationRef.getMappedName().getValue());
		}
		if(messageDestinationRef.getInjectionTarget()!=null && messageDestinationRef.getInjectionTarget().size()>0){
			messageDestinationRefTmp.setInjectionTarget(populateInjectionTarget(messageDestinationRef.getInjectionTarget()));
		}
		
		return messageDestinationRefTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.MessageDestination> populateMessageDestination(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("MessageDestinationType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("MessageDestinationType");
		
		List<com.sun.grizzly.http.webxml.schema.MessageDestination> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.MessageDestination>(list.size());
		for (JAXBElement obj : list) {
			MessageDestinationType item = (MessageDestinationType)obj.getValue();
			listTmp.add(getMessageDestination(item));
		}
		
		return listTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.MessageDestination getMessageDestination(MessageDestinationType messageDestination){
		
		if(messageDestination==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.MessageDestination messageDestinationTmp = new com.sun.grizzly.http.webxml.schema.MessageDestination();
		
		if(messageDestination.getDescription()!=null && messageDestination.getDescription().size()>0){
			messageDestinationTmp.setDescription(populateDescription(messageDestination.getDescription()));
		}
		if(messageDestination.getDisplayName()!=null && messageDestination.getDisplayName().size()>0){
			messageDestinationTmp.setDisplayName(populateDisplayName(messageDestination.getDisplayName()));
		}
		if(messageDestination.getMessageDestinationName()!=null){
			messageDestinationTmp.setMessageDestinationName(messageDestination.getMessageDestinationName().getValue());
		}
		if(messageDestination.getIcon()!=null && messageDestination.getIcon().size()>0){
			messageDestinationTmp.setIcon(populateIcon(messageDestination.getIcon()));
		}
		if(messageDestination.getMappedName()!=null){
			messageDestinationTmp.setMappedName(messageDestination.getMappedName().getValue());
		}
		
		return messageDestinationTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.ServiceRef> populateServiceRef(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("ServiceRefType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("ServiceRefType");
		
		List<com.sun.grizzly.http.webxml.schema.ServiceRef> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.ServiceRef>(list.size());
		for (JAXBElement obj : list) {
			ServiceRefType item = (ServiceRefType)obj.getValue();
			listTmp.add(getServiceRef(item));
		}
		
		return listTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.ServiceRef getServiceRef(ServiceRefType serviceRef){
		
		if(serviceRef==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ServiceRef serviceRefTmp = new com.sun.grizzly.http.webxml.schema.ServiceRef();
		
		if(serviceRef.getDescription()!=null && serviceRef.getDescription().size()>0){
			serviceRefTmp.setDescription(populateDescription(serviceRef.getDescription()));
		}
		if(serviceRef.getDisplayName()!=null && serviceRef.getDisplayName().size()>0){
			serviceRefTmp.setDisplayName(populateDisplayName(serviceRef.getDisplayName()));
		}
		if(serviceRef.getHandler()!=null && serviceRef.getHandler().size()>0){
			serviceRefTmp.setHandler(populateHandler(serviceRef.getHandler()));
		}
		if(serviceRef.getHandlerChains()!=null){
			serviceRefTmp.setHandlerChains(getHandlerChains(serviceRef.getHandlerChains()));
		}
		if(serviceRef.getIcon()!=null && serviceRef.getIcon().size()>0){
			serviceRefTmp.setIcon(populateIcon(serviceRef.getIcon()));
		}
		if(serviceRef.getMappedName()!=null){
			serviceRefTmp.setMappedName(serviceRef.getMappedName().getValue());
		}
		if(serviceRef.getInjectionTarget()!=null && serviceRef.getInjectionTarget().size()>0){
			serviceRefTmp.setInjectionTarget(populateInjectionTarget(serviceRef.getInjectionTarget()));
		}
		if(serviceRef.getJaxrpcMappingFile()!=null){
			serviceRefTmp.setJaxrpcMappingFile(serviceRef.getJaxrpcMappingFile().getValue());
		}
		if(serviceRef.getPortComponentRef()!=null && serviceRef.getPortComponentRef().size()>0){
			serviceRefTmp.setPortComponentRef(populatePortComponentRef(serviceRef.getPortComponentRef()));
		}
		if(serviceRef.getServiceInterface()!=null){
			serviceRefTmp.setServiceInterface(serviceRef.getServiceInterface().getValue());
		}
		if(serviceRef.getServiceQname()!=null){
			serviceRefTmp.setServiceQname(serviceRef.getServiceQname().getValue());
		}
		if(serviceRef.getServiceRefName()!=null){
			serviceRefTmp.setServiceRefName(serviceRef.getServiceRefName().getValue());
		}
		if(serviceRef.getServiceRefType()!=null){
			serviceRefTmp.setServiceRefType(serviceRef.getServiceRefType().getValue());
		}
		if(serviceRef.getWsdlFile()!=null){
			serviceRefTmp.setWsdlFile(serviceRef.getWsdlFile().getValue());
		}
		
		return serviceRefTmp;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.PortComponentRef> populatePortComponentRef(List<PortComponentRefType> list){
		
		if(list==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.PortComponentRef> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.PortComponentRef>(list.size());
		for (PortComponentRefType obj : list) {
			PortComponentRefType item = (PortComponentRefType)obj;
			listTmp.add(getPortComponentRef(item));
		}
		
		return listTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.PortComponentRef getPortComponentRef(PortComponentRefType portComponentRefType){
		
		if(portComponentRefType==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.PortComponentRef portComponentRefTmp = new com.sun.grizzly.http.webxml.schema.PortComponentRef();
		
		if(portComponentRefType.getEnableMtom()!=null){
			portComponentRefTmp.setEnableMtom(portComponentRefType.getEnableMtom().isValue());
		}
		if(portComponentRefType.getPortComponentLink()!=null){
			portComponentRefTmp.setPortComponentLink(portComponentRefType.getPortComponentLink().getValue());
		}
		if(portComponentRefType.getServiceEndpointInterface()!=null){
			portComponentRefTmp.setServiceEndpointInterface(portComponentRefType.getServiceEndpointInterface().getValue());
		}
	
		
		return portComponentRefTmp;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.ServiceRefHandler> populateHandler(List<ServiceRefHandlerType> list){
		
		if(list==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.ServiceRefHandler> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.ServiceRefHandler>(list.size());
		for (ServiceRefHandlerType obj : list) {
			ServiceRefHandlerType item = (ServiceRefHandlerType)obj;
			listTmp.add(getServiceRefHandler(item));
		}
		
		return listTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.ServiceRefHandlerChains getHandlerChains(ServiceRefHandlerChainsType serviceRefHandlerChains){
		
		if(serviceRefHandlerChains==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ServiceRefHandlerChains serviceRefHandlerChainsTmp = new com.sun.grizzly.http.webxml.schema.ServiceRefHandlerChains();
		
		if(serviceRefHandlerChains.getHandlerChain()!=null && serviceRefHandlerChains.getHandlerChain().size()>0){
			serviceRefHandlerChainsTmp.setHandlerChain(populateHandlerChain(serviceRefHandlerChains.getHandlerChain()));
		}
		
		return serviceRefHandlerChainsTmp;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.ServiceRefHandlerChain> populateHandlerChain(List<ServiceRefHandlerChainType> list){
		
		if(list==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.ServiceRefHandlerChain> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.ServiceRefHandlerChain>(list.size());
		for (ServiceRefHandlerChainType obj : list) {
			ServiceRefHandlerChainType item = (ServiceRefHandlerChainType)obj;
			listTmp.add(getServiceRefHandlerChain(item));
		}
		
		return listTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.ServiceRefHandlerChain getServiceRefHandlerChain(ServiceRefHandlerChainType serviceRefHandlerChain){
		
		if(serviceRefHandlerChain==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ServiceRefHandlerChain serviceRefHandlerChainsTmp = new com.sun.grizzly.http.webxml.schema.ServiceRefHandlerChain();
		
		if(serviceRefHandlerChain.getHandler()!=null && serviceRefHandlerChain.getHandler().size()>0){
			serviceRefHandlerChainsTmp.setHandler(populateHandler(serviceRefHandlerChain.getHandler()));
		}
		if(serviceRefHandlerChain.getPortNamePattern()!=null){
			serviceRefHandlerChainsTmp.setPortNamePattern(serviceRefHandlerChain.getPortNamePattern());
		}
		if(serviceRefHandlerChain.getProtocolBindings()!=null && serviceRefHandlerChain.getHandler().size()>0){
			serviceRefHandlerChainsTmp.setProtocolBindings(serviceRefHandlerChain.getProtocolBindings());
		}
		if(serviceRefHandlerChain.getServiceNamePattern()!=null){
			serviceRefHandlerChainsTmp.setServiceNamePattern(serviceRefHandlerChain.getServiceNamePattern());
		}
		
		return serviceRefHandlerChainsTmp;
	}
	
	
	private com.sun.grizzly.http.webxml.schema.ServiceRefHandler getServiceRefHandler(ServiceRefHandlerType serviceRefHandler){
		
		if(serviceRefHandler==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ServiceRefHandler serviceRefHandlerTmp = new com.sun.grizzly.http.webxml.schema.ServiceRefHandler();
		
		if(serviceRefHandler.getDescription()!=null && serviceRefHandler.getDescription().size()>0){
			serviceRefHandlerTmp.setDescription(populateDescription(serviceRefHandler.getDescription()));
		}
		if(serviceRefHandler.getDisplayName()!=null && serviceRefHandler.getDisplayName().size()>0){
			serviceRefHandlerTmp.setDisplayName(populateDisplayName(serviceRefHandler.getDisplayName()));
		}
		if(serviceRefHandler.getHandlerClass()!=null){
			serviceRefHandlerTmp.setHandlerClass(serviceRefHandler.getHandlerClass().getValue());
		}
		if(serviceRefHandler.getHandlerName()!=null){
			serviceRefHandlerTmp.setHandlerName(serviceRefHandler.getHandlerName().getValue());
		}
		if(serviceRefHandler.getPortName()!=null && serviceRefHandler.getPortName().size()>0){
			serviceRefHandlerTmp.setPortName(populatePortName(serviceRefHandler.getPortName()));
		}
		if(serviceRefHandler.getSoapRole()!=null && serviceRefHandler.getSoapRole().size()>0){
			serviceRefHandlerTmp.setSoapRole(populateSoapRole(serviceRefHandler.getSoapRole()));
		}
		if(serviceRefHandler.getSoapHeader()!=null && serviceRefHandler.getSoapHeader().size()>0){
			serviceRefHandlerTmp.setSoapHeader(populateSoapHeader(serviceRefHandler.getSoapHeader()));
		}
		
		List<ParamValueType> initParams = serviceRefHandler.getInitParam();
		
		if(initParams!=null){
			List<com.sun.grizzly.http.webxml.schema.InitParam> initParamsTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.InitParam>(initParams.size());
			for (ParamValueType initParam : initParams) {
				initParamsTmp.add(getInitParam(initParam));
			}
			
			serviceRefHandlerTmp.setInitParam(initParamsTmp);
		}
		
		return serviceRefHandlerTmp;
	}
	
	private List<QName> populateSoapHeader(List<XsdQNameType> list){
		
		if(list==null){
			return null;
		}
		
		List<QName> listTmp = new ArrayList<QName>(list.size());
		for (XsdQNameType obj : list) {
			QName item = (QName)obj.getValue();
			listTmp.add(item);
		}
		
		return listTmp;
	}
	
	private List<String> populatePortName(List<com.sun.grizzly.http.webxml.schema.version_3_0.String> list){
		
		if(list==null){
			return null;
		}
		
		List<String> listTmp = new ArrayList<String>(list.size());
		for (com.sun.grizzly.http.webxml.schema.version_3_0.String obj : list) {
			listTmp.add(((com.sun.grizzly.http.webxml.schema.version_3_0.String) obj).getValue());
		}
		
		return listTmp;
	}
	
	private List<String> populateSoapRole(List<com.sun.grizzly.http.webxml.schema.version_3_0.String> list){
		
		if(list==null){
			return null;
		}
		
		List<String> listTmp = new ArrayList<String>(list.size());
		for (com.sun.grizzly.http.webxml.schema.version_3_0.String obj : list) {
			listTmp.add(((com.sun.grizzly.http.webxml.schema.version_3_0.String) obj).getValue());
		}
		
		return listTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.SecurityConstraint> populateSecurityConstraint(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("SecurityConstraintType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("SecurityConstraintType");
		
		List<com.sun.grizzly.http.webxml.schema.SecurityConstraint> securityConstraintList = new ArrayList<com.sun.grizzly.http.webxml.schema.SecurityConstraint>(list.size());
		for (JAXBElement obj : list) {
			SecurityConstraintType security = (SecurityConstraintType)obj.getValue();
			securityConstraintList.add(getSecurityConstraint(security));
		}
		
		return securityConstraintList;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.InjectionTarget> populateInjectionTarget(List<InjectionTargetType> list){
		
		if(list==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.InjectionTarget> tmpList = new ArrayList<com.sun.grizzly.http.webxml.schema.InjectionTarget>(list.size());
		for (InjectionTargetType obj : list) {
			InjectionTargetType item = (InjectionTargetType)obj;
			tmpList.add(getInjectionTarget(item));
		}
		
		return tmpList;
	}
	
	private com.sun.grizzly.http.webxml.schema.InjectionTarget getInjectionTarget(InjectionTargetType injectionTarget){
		
		if(injectionTarget==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.InjectionTarget injectionTargetTmp = new com.sun.grizzly.http.webxml.schema.InjectionTarget();
		
		if(injectionTarget.getInjectionTargetClass()!=null){
			injectionTargetTmp.setInjectionTargetClass(injectionTarget.getInjectionTargetClass().getValue());
		}
		if(injectionTarget.getInjectionTargetName()!=null){
			injectionTargetTmp.setInjectionTargetName(injectionTarget.getInjectionTargetName().getValue());
		}
		
		return injectionTargetTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.JspConfig> populateJspConfig(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("JspConfigType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("JspConfigType");
		
		List<com.sun.grizzly.http.webxml.schema.JspConfig> jspConfigList = new ArrayList<com.sun.grizzly.http.webxml.schema.JspConfig>(list.size());
		for (JAXBElement obj : list) {
			JspConfigType item = (JspConfigType)obj.getValue();
			jspConfigList.add(getJspConfig(item));
		}
		
		return jspConfigList;
	}
	
	private com.sun.grizzly.http.webxml.schema.JspConfig getJspConfig(JspConfigType jspConfig){
		
		if(jspConfig==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.JspConfig jspConfigTmp = new com.sun.grizzly.http.webxml.schema.JspConfig();
		
		if(jspConfig.getJspPropertyGroup()!=null){
			jspConfigTmp.setJspPropertyGroup(populateJspPropertyGroup(jspConfig.getJspPropertyGroup()));
		}
		if(jspConfig.getTaglib()!=null){
			jspConfigTmp.setTaglib(populateTaglib(jspConfig.getTaglib()));
		}
		
		return jspConfigTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.JspPropertyGroup getJspPropertyGroup(JspPropertyGroupType jspPropertyGroupType){
		
		if(jspPropertyGroupType==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.JspPropertyGroup jspPropertyGroup = new com.sun.grizzly.http.webxml.schema.JspPropertyGroup();
		
		jspPropertyGroup.setBuffer(jspPropertyGroupType.getBuffer().getValue());
		jspPropertyGroup.setDefaultContentType(jspPropertyGroupType.getDefaultContentType().getValue());
		jspPropertyGroup.setDeferredSyntaxAllowedAsLiteral(jspPropertyGroupType.getDeferredSyntaxAllowedAsLiteral().isValue());
		jspPropertyGroup.setDescription(populateDescription(jspPropertyGroupType.getDescription()));
		jspPropertyGroup.setDisplayName(populateDisplayName(jspPropertyGroupType.getDisplayName()));
		jspPropertyGroup.setElIgnored(jspPropertyGroupType.getElIgnored().isValue());
		jspPropertyGroup.setErrorOnUndeclaredNamespace(jspPropertyGroupType.getErrorOnUndeclaredNamespace().isValue());
		jspPropertyGroup.setIcon(populateIcon(jspPropertyGroupType.getIcon()));
		jspPropertyGroup.setIncludeCoda(populateIncludeCoda(jspPropertyGroupType.getIncludeCoda()));
		jspPropertyGroup.setIncludePrelude(populateIncludePrelude(jspPropertyGroupType.getIncludePrelude()));
		jspPropertyGroup.setIsXml(jspPropertyGroupType.getIsXml().isValue());
		jspPropertyGroup.setPageEncoding(jspPropertyGroupType.getPageEncoding().getValue());
		jspPropertyGroup.setScriptingInvalid(jspPropertyGroupType.getScriptingInvalid().isValue());
		jspPropertyGroup.setTrimDirectiveWhitespaces(jspPropertyGroupType.getTrimDirectiveWhitespaces().isValue());
		jspPropertyGroup.setUrlPattern(populateUrlPattern(jspPropertyGroupType.getUrlPattern()));
		
		
		return jspPropertyGroup;
	}
	
	private List<String> populateUrlPattern(List<UrlPatternType> list){
		
		if(list==null){
			return null;
		}
		
		List<String> listTmp = new ArrayList<String>(list.size());
		for (UrlPatternType obj : list) {
			listTmp.add(((UrlPatternType) obj).getValue());
		}
		
		return listTmp;
	}
	
	private List<String> populateIncludePrelude(List<PathType> list){
		
		if(list==null){
			return null;
		}
		
		List<String> listTmp = new ArrayList<String>(list.size());
		for (PathType obj : list) {
			listTmp.add(((PathType) obj).getValue());
		}
		
		return listTmp;
	}
	
	private List<String> populateIncludeCoda(List<PathType> list){
		
		if(list==null){
			return null;
		}
		
		List<String> listTmp = new ArrayList<String>(list.size());
		for (PathType obj : list) {
			listTmp.add(((PathType) obj).getValue());
		}
		
		return listTmp;
	}
	
	private List<String> populateDescription(List<DescriptionType> list){
		
		if(list==null){
			return null;
		}
		
		List<String> descriptionListTmp = new ArrayList<String>(list.size());
		for (DescriptionType obj : list) {
			descriptionListTmp.add(((DescriptionType) obj).getValue());
		}
		
		return descriptionListTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<String> populateDescription(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("DescriptionType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("DescriptionType");
		
		if(list==null){
			return null;
		}
		
		List<String> listTmp = new ArrayList<String>(list.size());
		for (JAXBElement obj : list) {
			DescriptionType item = (DescriptionType) obj.getValue();
			listTmp.add(item.getValue());
		}
		return listTmp;
	}
	
	
	
	private com.sun.grizzly.http.webxml.schema.SecurityConstraint getSecurityConstraint(SecurityConstraintType securityConstraint){
		
		if(securityConstraint==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.SecurityConstraint securityConstraintTmp = new com.sun.grizzly.http.webxml.schema.SecurityConstraint();
		
		if(securityConstraint.getAuthConstraint()!=null){
			securityConstraintTmp.setAuthConstraint(getAuthConstraint(securityConstraint.getAuthConstraint()));
		}
		if(securityConstraint.getDisplayName()!=null && securityConstraint.getDisplayName().size()>0){
			securityConstraintTmp.setDisplayName(populateDisplayName(securityConstraint.getDisplayName()));
		}
		if(securityConstraint.getUserDataConstraint()!=null){
			
			com.sun.grizzly.http.webxml.schema.UserDataConstraint userData =  new com.sun.grizzly.http.webxml.schema.UserDataConstraint();
			
			if(securityConstraint.getUserDataConstraint().getDescription()!=null && securityConstraint.getUserDataConstraint().getDescription().size()>0){
				userData.setDescription(populateDescription(securityConstraint.getUserDataConstraint().getDescription()));
			}
			if(securityConstraint.getUserDataConstraint().getTransportGuarantee()!=null){
				userData.setTransportGuarantee(securityConstraint.getUserDataConstraint().getTransportGuarantee().getValue());
			}
			
			securityConstraintTmp.setUserDataConstraint(userData);
		}
		if(securityConstraint.getWebResourceCollection()!=null){
			securityConstraintTmp.setWebResourceCollection(populateWebResourceCollection(securityConstraint.getWebResourceCollection()));
		}
		
		return securityConstraintTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.LoginConfig> populateLoginConfig(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("LoginConfigType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("LoginConfigType");
		
		List<com.sun.grizzly.http.webxml.schema.LoginConfig> loginConfigList = new ArrayList<com.sun.grizzly.http.webxml.schema.LoginConfig>(list.size());
		for (JAXBElement obj : list) {
			LoginConfigType config = (LoginConfigType) obj.getValue();
			loginConfigList.add(getLoginConfig(config));
		}
		
		return loginConfigList;
	}
	
	@SuppressWarnings("unchecked")
	private List<String> populateDisplayName(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("DisplayNameType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("DisplayNameType");
		
		if(list==null){
			return null;
		}
		
		List<String> listTmp = new ArrayList<String>(list.size());
		for (JAXBElement obj : list) {
			DisplayNameType item = (DisplayNameType) obj.getValue();
			listTmp.add(item.getValue());
		}
		return listTmp;
	}
	
	private List<String> populateDisplayName(List<DisplayNameType> list){
		
		if(list==null){
			return null;
		}
		
		List<String> listTmp = new ArrayList<String>(list.size());
		for (DisplayNameType obj : list) {
			listTmp.add(((DisplayNameType) obj).getValue());
		}
		
		return listTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.WelcomeFileList> populateWelcomeFileList(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("WelcomeFileListType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("WelcomeFileListType");
		
		List<com.sun.grizzly.http.webxml.schema.WelcomeFileList> welcomeFileList = new ArrayList<com.sun.grizzly.http.webxml.schema.WelcomeFileList>(list.size());
		for (JAXBElement obj : list) {
			WelcomeFileListType welcome = (WelcomeFileListType) obj.getValue();
			welcomeFileList.add(getWelcomeFileList(welcome));
		}
		
		return welcomeFileList;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.SessionConfig> populateSessionConfig(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("SessionConfigType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("SessionConfigType");
		
		List<com.sun.grizzly.http.webxml.schema.SessionConfig> sessionConfigList = new ArrayList<com.sun.grizzly.http.webxml.schema.SessionConfig>(list.size());
		for (JAXBElement obj : list) {
			SessionConfigType config = (SessionConfigType) obj.getValue();
			sessionConfigList.add(getSessionConfig(config));
		}
		
		return sessionConfigList;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.FilterMapping> populateFilterMapping(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("FilterMappingType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("FilterMappingType");
		
		List<com.sun.grizzly.http.webxml.schema.FilterMapping> filterMappingList = new ArrayList<com.sun.grizzly.http.webxml.schema.FilterMapping>(list.size());
		for (JAXBElement obj : list) {
			FilterMappingType mapping = (FilterMappingType) obj.getValue();
			filterMappingList.add(getFilterMapping(mapping));
		}
		
		return filterMappingList;
	}

	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.EnvEntry> populateEnvEntry(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("EnvEntryType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("EnvEntryType");
		
		List<com.sun.grizzly.http.webxml.schema.EnvEntry> envEntryList = new ArrayList<com.sun.grizzly.http.webxml.schema.EnvEntry>(list.size());
		for (JAXBElement obj : list) {
			EnvEntryType env = (EnvEntryType)obj.getValue();
			envEntryList.add(getEnvEntry(env));
		}
		
		return envEntryList;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.EjbLocalRef> populateEjbLocalRef(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("EjbLocalRefType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("EjbLocalRefType");
		
		List<com.sun.grizzly.http.webxml.schema.EjbLocalRef> ejbLocalRefList = new ArrayList<com.sun.grizzly.http.webxml.schema.EjbLocalRef>(list.size());
		for (JAXBElement obj : list) {
			EjbLocalRefType ejb = (EjbLocalRefType)obj.getValue();
			ejbLocalRefList.add(getEjbLocalRef(ejb));
		}
		
		return ejbLocalRefList;
	}
	
	private com.sun.grizzly.http.webxml.schema.EjbLocalRef getEjbLocalRef(EjbLocalRefType ejb){
		if(ejb==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.EjbLocalRef ejbLocalRefTmp = new com.sun.grizzly.http.webxml.schema.EjbLocalRef();
		
		if(ejb.getEjbLink()!=null){
			ejbLocalRefTmp.setEjbLink(ejb.getEjbLink().getValue());
		}
		if(ejb.getEjbRefName()!=null){
			ejbLocalRefTmp.setEjbRefName(ejb.getEjbRefName().getValue());
		}
		if(ejb.getEjbRefType()!=null){
			ejbLocalRefTmp.setEjbRefType(ejb.getEjbRefType().getValue());
		}
		if(ejb.getDescription()!=null && ejb.getDescription().size()>0){
			ejbLocalRefTmp.setDescription(populateDescription(ejb.getDescription()));
		}
		if(ejb.getLocal()!=null){
			ejbLocalRefTmp.setLocal(ejb.getLocal().getValue());
		}
		if(ejb.getLocalHome()!=null){
			ejbLocalRefTmp.setLocalHome(ejb.getLocalHome().getValue());
		}
		if(ejb.getMappedName()!=null){
			ejbLocalRefTmp.setMappedName(ejb.getMappedName().getValue());
		}
		if(ejb.getInjectionTarget()!=null && ejb.getInjectionTarget().size()>0){
			ejbLocalRefTmp.setInjectionTarget(populateInjectionTarget(ejb.getInjectionTarget()));
		}
		
		return ejbLocalRefTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.Listener> populateListener(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("ListenerType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("ListenerType");
		
		List<com.sun.grizzly.http.webxml.schema.Listener> contextParamList = new ArrayList<com.sun.grizzly.http.webxml.schema.Listener>(list.size());
		for (JAXBElement obj : list) {
			ListenerType listener = (ListenerType)obj.getValue();
			contextParamList.add(getListener(listener));
		}
		
		return contextParamList;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.Filter> populateFilter(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("FilterType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("FilterType");
		
		List<com.sun.grizzly.http.webxml.schema.Filter> filterList = new ArrayList<com.sun.grizzly.http.webxml.schema.Filter>(list.size());
		for (JAXBElement obj : list) {
			FilterType filter = (FilterType)obj.getValue();
			
			com.sun.grizzly.http.webxml.schema.Filter filterTmp = new com.sun.grizzly.http.webxml.schema.Filter();
			
			if(filter.getIcon()!=null && filter.getIcon().size()>0){
				filterTmp.setIcon(populateIcon(filter.getIcon()));
			}
			if(filter.getDescription()!=null && filter.getDescription().size()>0){
				filterTmp.setDescription(populateDescription(filter.getDescription()));
			}
			if(filter.getDisplayName()!=null && filter.getDisplayName().size()>0){
				filterTmp.setDisplayName(populateDisplayName(filter.getDisplayName()));
			}
			if(filter.getFilterName()!=null){
				filterTmp.setFilterName(filter.getFilterName().getValue());
			}
			if(filter.getFilterClass()!=null){
				filterTmp.setFilterClass(filter.getFilterClass().getValue());
			}
			if(filter.getAsyncSupported()!=null){
				filterTmp.setAsyncSupported(true);
			}
			if(filter.getAsyncTimeout()!=null){
				filterTmp.setAsyncTimeout(filter.getAsyncTimeout().getValue().toString());
			}
			
			List<ParamValueType> initParams = filter.getInitParam();
			
			if(initParams!=null){
				List<com.sun.grizzly.http.webxml.schema.InitParam> initParamsTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.InitParam>(initParams.size());
				for (ParamValueType initParam : initParams) {
					initParamsTmp.add(getInitParam(initParam));
				}
				
				filterTmp.setInitParam(initParamsTmp);
			}
			
			filterList.add(filterTmp);
		}
		
		return filterList;
	}
	
	private com.sun.grizzly.http.webxml.schema.InitParam getInitParam(ParamValueType initParam){
		
		if(initParam==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.InitParam initParamTmp = new com.sun.grizzly.http.webxml.schema.InitParam();
		
		if(initParam.getParamName()!=null){
			initParamTmp.setParamName(initParam.getParamName().getValue());
		}
		if(initParam.getParamValue()!=null){
			initParamTmp.setParamValue(initParam.getParamValue().getValue());
		}
		if(initParam.getDescription()!=null && initParam.getDescription().size()>0){
			initParamTmp.setDescription(populateDescription(initParam.getDescription()));
		}
		
		return initParamTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.FilterMapping getFilterMapping(FilterMappingType filterMapping){
		
		if(filterMapping==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.FilterMapping filterMappingTmp = new com.sun.grizzly.http.webxml.schema.FilterMapping();
		
		if(filterMapping.getFilterName()!=null){
			filterMappingTmp.setFilterName(filterMapping.getFilterName().getValue());
		}
		if(filterMapping.getUrlPatternOrServletName()!=null){
			
			List<Object> list = filterMapping.getUrlPatternOrServletName();
			
			if(list!=null){
				
				List<String> urlPatternList = new ArrayList<String>();
				List<String> servletNameList = new ArrayList<String>();
				
				for (Object object : list) {
					
					if(object instanceof UrlPattern){
						urlPatternList.add(((UrlPattern)object).getvalue());
					} else if(object instanceof ServletName){
						servletNameList.add(((ServletName)object).getvalue());
					}
				}
				filterMappingTmp.setUrlPattern(urlPatternList);
				filterMappingTmp.setServletName(servletNameList);
			}
		}
		if(filterMapping.getDispatcher()!=null){
			filterMappingTmp.setDispatcher(populateDispatcher(filterMapping.getDispatcher()));
		}
		
		return filterMappingTmp;
	}
	
	private List<String> populateDispatcher(List<DispatcherType> list){
		
		if(list==null){
			return null;
		}
		
		List<String> listTmp = new ArrayList<String>(list.size());
		for (DispatcherType obj : list) {
			listTmp.add(((DispatcherType) obj).getValue());
		}
		
		return listTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.Listener getListener(ListenerType listener){
		
		if(listener==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.Listener listenerTmp = new com.sun.grizzly.http.webxml.schema.Listener();
		
		if(listener.getListenerClass()!=null){
			listenerTmp.setListenerClass(listener.getListenerClass().getValue());
		}
		if(listener.getIcon()!=null && listener.getIcon().size()>0){
			listenerTmp.setIcon(populateIcon(listener.getIcon()));
		}
		if(listener.getDescription()!=null && listener.getDescription().size()>0){
			listenerTmp.setDescription(populateDescription(listener.getDescription()));
		}
		if(listener.getDisplayName()!=null && listener.getDisplayName().size()>0){
			listenerTmp.setDisplayName(populateDisplayName(listener.getDisplayName()));
		}

		return listenerTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.SecurityRoleRef getSecurityRoleRef(SecurityRoleRefType securityRoleRef){
		
		if(securityRoleRef==null){
			return null;
		}
		com.sun.grizzly.http.webxml.schema.SecurityRoleRef srf = new com.sun.grizzly.http.webxml.schema.SecurityRoleRef();
		
		if(securityRoleRef.getRoleName()!=null){
			srf.setRoleName(securityRoleRef.getRoleName().getValue());
		}
		if(securityRoleRef.getRoleLink()!=null){
			srf.setRoleLink(securityRoleRef.getRoleLink().getValue());
		}
		if(securityRoleRef.getDescription()!=null && securityRoleRef.getDescription().size()>0){
			srf.setDescription(populateDescription(securityRoleRef.getDescription()));
		}
		
		return srf;
		
	}
	
	private com.sun.grizzly.http.webxml.schema.Icon getIcon(IconType icon){
		
		if(icon==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.Icon iconTmp = new com.sun.grizzly.http.webxml.schema.Icon();
		
		if(icon.getSmallIcon()!=null){
			iconTmp.setSmallIcon(icon.getSmallIcon().getValue());
		}
		if(icon.getLargeIcon()!=null){
			iconTmp.setLargeIcon(icon.getLargeIcon().getValue());
		}
		
		return iconTmp;
		
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.Icon> populateIcon(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("DescriptionType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("IconType");
		
		if(list==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.Icon> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.Icon>(list.size());
		for (JAXBElement obj : list) {
			IconType item = (IconType) obj.getValue();
			listTmp.add(getIcon(item));
		}
		return listTmp;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.Icon> populateIcon(List<IconType> list){
		
		if(list==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.Icon> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.Icon>(list.size());
		for (IconType obj : list) {
			listTmp.add(getIcon(obj));
		}
		
		return listTmp;
	}

	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.EjbRef> populateEjbRef(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("EjbRefType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("EjbRefType");
		
		List<com.sun.grizzly.http.webxml.schema.EjbRef> ejbRefList = new ArrayList<com.sun.grizzly.http.webxml.schema.EjbRef>(list.size());
		for (JAXBElement obj : list) {
			EjbRefType ejb = (EjbRefType)obj.getValue();
			ejbRefList.add(getEjbRef(ejb));
		}
		
		return ejbRefList;
	}

	private com.sun.grizzly.http.webxml.schema.EjbRef getEjbRef(EjbRefType ejb){
		if(ejb==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.EjbRef ejbRefTmp = new com.sun.grizzly.http.webxml.schema.EjbRef();
		
		if(ejb.getEjbLink()!=null){
			ejbRefTmp.setEjbLink(ejb.getEjbLink().getValue());
		}
		if(ejb.getEjbRefName()!=null){
			ejbRefTmp.setEjbRefName(ejb.getEjbRefName().getValue());
		}
		if(ejb.getEjbRefType()!=null){
			ejbRefTmp.setEjbRefType(ejb.getEjbRefType().getValue());
		}
		if(ejb.getDescription()!=null && ejb.getDescription().size()>0){
			ejbRefTmp.setDescription(populateDescription(ejb.getDescription()));
		}
		if(ejb.getEjbLink()!=null){
			ejbRefTmp.setEjbLink(ejb.getEjbLink().getValue());
		}
		if(ejb.getHome()!=null){
			ejbRefTmp.setHome(ejb.getHome().getValue());
		}
		if(ejb.getRemote()!=null){
			ejbRefTmp.setRemote(ejb.getRemote().getValue());
		}
		if(ejb.getMappedName()!=null){
			ejbRefTmp.setMappedName(ejb.getMappedName().getValue());
		}
		if(ejb.getInjectionTarget()!=null && ejb.getInjectionTarget().size()>0){
			ejbRefTmp.setInjectionTarget(populateInjectionTarget(ejb.getInjectionTarget()));
		}
		
		return ejbRefTmp;
	}

	private com.sun.grizzly.http.webxml.schema.EnvEntry getEnvEntry(EnvEntryType envEntry){
		
		if(envEntry==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.EnvEntry envEntryTmp = new com.sun.grizzly.http.webxml.schema.EnvEntry();
		
		if(envEntry.getEnvEntryName()!=null){
			envEntryTmp.setEnvEntryName(envEntry.getEnvEntryName().getValue());
		}
		if(envEntry.getEnvEntryType()!=null){
			envEntryTmp.setEnvEntryType(envEntry.getEnvEntryType().getValue());
		}
		if(envEntry.getEnvEntryValue()!=null){
			envEntryTmp.setEnvEntryValue(envEntry.getEnvEntryValue().getValue());
		}
		if(envEntry.getDescription()!=null && envEntry.getDescription().size()>0){
			envEntryTmp.setDescription(populateDescription(envEntry.getDescription()));
		}
		if(envEntry.getMappedName()!=null){
			envEntryTmp.setMappedName(envEntry.getMappedName().getValue());
		}
		if(envEntry.getInjectionTarget()!=null && envEntry.getInjectionTarget().size()>0){
			envEntryTmp.setInjectionTarget(populateInjectionTarget(envEntry.getInjectionTarget()));
		}
		
		return envEntryTmp;
	}

	private com.sun.grizzly.http.webxml.schema.ErrorPage getErrorPage(ErrorPageType errorPage){
		
		if(errorPage==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ErrorPage errorPageTmp = new com.sun.grizzly.http.webxml.schema.ErrorPage();
		
		if(errorPage.getLocation()!=null){
			errorPageTmp.setLocation(errorPage.getLocation().getValue());
		}
		if(errorPage.getErrorCode()!=null){
			errorPageTmp.setErrorCode(errorPage.getErrorCode().getValue().toString());
		}
		if(errorPage.getExceptionType()!=null){
			errorPageTmp.setExceptionType(errorPage.getExceptionType().getValue());
		}
		
		return errorPageTmp;
	}

	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.ErrorPage> populateErrorPage(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("ErrorPageType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("ErrorPageType");
		
		List<com.sun.grizzly.http.webxml.schema.ErrorPage> errorPageList = new ArrayList<com.sun.grizzly.http.webxml.schema.ErrorPage>(list.size());
		for (JAXBElement obj : list) {
			ErrorPageType page = (ErrorPageType)obj.getValue();
			errorPageList.add(getErrorPage(page));
		}
		
		return errorPageList;
	}

	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.MimeMapping> populateMimeMapping(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("MimeMappingType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("MimeMappingType");
		
		List<com.sun.grizzly.http.webxml.schema.MimeMapping> mimeMappingList = new ArrayList<com.sun.grizzly.http.webxml.schema.MimeMapping>(list.size());
		for (JAXBElement obj : list) {
			MimeMappingType mapping = (MimeMappingType)obj.getValue();
			mimeMappingList.add(getMimeMapping(mapping));
		}
		
		return mimeMappingList;
	}

	private com.sun.grizzly.http.webxml.schema.MimeMapping getMimeMapping(MimeMappingType mimeMapping){
		
		if(mimeMapping==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.MimeMapping mimeMappingTmp = new com.sun.grizzly.http.webxml.schema.MimeMapping();
		
		if(mimeMapping.getExtension()!=null){
			mimeMappingTmp.setExtension(mimeMapping.getExtension().getValue());
		}
		if(mimeMapping.getMimeType()!=null){
			mimeMappingTmp.setMimeType(mimeMapping.getMimeType().getValue());
		}
		
		return mimeMappingTmp;
	}

	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.ResourceEnvRef> populateResourceEnvRef(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("ResourceEnvRefType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("ResourceEnvRefType");
		
		List<com.sun.grizzly.http.webxml.schema.ResourceEnvRef> resourceEnvRefList = new ArrayList<com.sun.grizzly.http.webxml.schema.ResourceEnvRef>(list.size());
		for (JAXBElement obj : list) {
			ResourceEnvRefType res = (ResourceEnvRefType)obj.getValue();
			resourceEnvRefList.add(getResourceEnvRef(res));
		}
		
		return resourceEnvRefList;
	}

	private com.sun.grizzly.http.webxml.schema.ResourceRef getResourceRef(ResourceRefType resourceRef){
		
		if(resourceRef==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ResourceRef resourceRefTmp = new com.sun.grizzly.http.webxml.schema.ResourceRef();
		
		if(resourceRef.getResRefName()!=null){
			resourceRefTmp.setResRefName(resourceRef.getResRefName().getValue());
		}
		if(resourceRef.getResAuth()!=null){
			resourceRefTmp.setResAuth(resourceRef.getResAuth().getValue());
		}
		if(resourceRef.getResSharingScope()!=null){
			resourceRefTmp.setResSharingScope(resourceRef.getResSharingScope().getValue());
		}
		if(resourceRef.getResType()!=null){
			resourceRefTmp.setResType(resourceRef.getResType().getValue());
		}
		if(resourceRef.getDescription()!=null && resourceRef.getDescription().size()>0){
			resourceRefTmp.setDescription(populateDescription(resourceRef.getDescription()));
		}
		if(resourceRef.getMappedName()!=null){
			resourceRefTmp.setMappedName(resourceRef.getMappedName().getValue());
		}
		if(resourceRef.getInjectionTarget()!=null && resourceRef.getInjectionTarget().size()>0){
			resourceRefTmp.setInjectionTarget(populateInjectionTarget(resourceRef.getInjectionTarget()));
		}
		
		return resourceRefTmp;
	}

	private com.sun.grizzly.http.webxml.schema.AuthConstraint getAuthConstraint(AuthConstraintType authConstraint){
		
		if(authConstraint==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.AuthConstraint authConstraintTmp = new com.sun.grizzly.http.webxml.schema.AuthConstraint();
		
		if(authConstraint.getRoleName()!=null){
			
			List<RoleNameType> list = authConstraint.getRoleName();
			if(list!=null){
				
				List<String> roleList = new ArrayList<String>(list.size());
				for (RoleNameType roleName : list) {
					roleList.add(roleName.getValue());
				}
				
				authConstraintTmp.setRoleName(roleList);
			}
			
		}
	
		if(authConstraint.getDescription()!=null && authConstraint.getDescription().size()>0){
			authConstraintTmp.setDescription(populateDescription(authConstraint.getDescription()));
		}
		
		return authConstraintTmp;
	}

	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.ServletMapping> populateServletMapping(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("ServletMappingType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("ServletMappingType");
		
		List<com.sun.grizzly.http.webxml.schema.ServletMapping> servletMappingList = new ArrayList<com.sun.grizzly.http.webxml.schema.ServletMapping>(list.size());
		for (JAXBElement obj : list) {
			ServletMappingType mapping = (ServletMappingType)obj.getValue();
			servletMappingList.add(getServletMapping(mapping));
		}
		
		return servletMappingList;
	}

	private com.sun.grizzly.http.webxml.schema.WebResourceCollection getWebResourceCollection(WebResourceCollectionType webResourceCollection){
		
		if(webResourceCollection==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.WebResourceCollection webResourceCollectionTmp = new com.sun.grizzly.http.webxml.schema.WebResourceCollection();
		
		if(webResourceCollection.getHttpMethod()!=null){
			List<String> list = webResourceCollection.getHttpMethod();
			if(list!=null){
				webResourceCollectionTmp.setHttpMethod(list);
			}
		}
		
		if(webResourceCollection.getUrlPattern()!=null){
			List<UrlPatternType> list = webResourceCollection.getUrlPattern();
			if(list!=null){
				
				List<String> urlPatternList = new ArrayList<String>(list.size());
				for (UrlPatternType url : list) {
					urlPatternList.add(url.getValue());
				}
				
				webResourceCollectionTmp.setUrlPattern(urlPatternList);
			}
		}
		if(webResourceCollection.getDescription()!=null && webResourceCollection.getDescription().size()>0){
			webResourceCollectionTmp.setDescription(populateDescription(webResourceCollection.getDescription()));
		}
		if(webResourceCollection.getWebResourceName()!=null){
			webResourceCollectionTmp.setWebResourceName(webResourceCollection.getWebResourceName().getValue());
		}
		
		return webResourceCollectionTmp;
	}

	private com.sun.grizzly.http.webxml.schema.SessionConfig getSessionConfig(SessionConfigType sessionConfig){
		
		if(sessionConfig==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.SessionConfig sessionConfigTmp = new com.sun.grizzly.http.webxml.schema.SessionConfig();
		
		if(sessionConfig.getSessionTimeout()!=null){
			sessionConfigTmp.setSessionTimeout(sessionConfig.getSessionTimeout().getValue().toString());
		}
		if(sessionConfig.getCookieConfig()!=null){
			sessionConfigTmp.setCookieConfig(getCookieConfig(sessionConfig.getCookieConfig()));
		}
		if(sessionConfig.getTrackingMode()!=null){
			sessionConfigTmp.setTrackingMode(populateTrackingMode(sessionConfig.getTrackingMode()));
		}
		
		return sessionConfigTmp;
	}
	
	private List<String> populateTrackingMode(List<TrackingModeType> list){
		
		if(list==null){
			return null;
		}
		
		List<String> listTmp = new ArrayList<String>(list.size());
		for (TrackingModeType obj : list) {
			listTmp.add(((TrackingModeType) obj).getValue());
		}
		
		return listTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.CookieConfig getCookieConfig(CookieConfigType cookieConfig){
		
		if(cookieConfig==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.CookieConfig cookieConfigTmp = new com.sun.grizzly.http.webxml.schema.CookieConfig();
		
		if(cookieConfig.getName()!=null){
			cookieConfigTmp.setName(cookieConfig.getName().getValue());
		}
		if(cookieConfig.getComment()!=null){
			cookieConfigTmp.setComment(cookieConfig.getComment().getValue());
		}
		if(cookieConfig.getDomain()!=null){
			cookieConfigTmp.setDomain(cookieConfig.getDomain().getValue());
		}
		if(cookieConfig.getPath()!=null){
			cookieConfigTmp.setPath(cookieConfig.getPath().getValue());
		}
		if(cookieConfig.getHttpOnly()!=null){
			cookieConfigTmp.setHttpOnly(cookieConfig.getHttpOnly().isValue());
		}
		if(cookieConfig.getSecure()!=null){
			cookieConfigTmp.setSecure(cookieConfig.getSecure().isValue());
		}
		
		return cookieConfigTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.ServletMapping getServletMapping(ServletMappingType servletMapping){
		
		if(servletMapping==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ServletMapping servletMappingTmp = new com.sun.grizzly.http.webxml.schema.ServletMapping();
		
		if(servletMapping.getServletName()!=null){
			servletMappingTmp.setServletName(servletMapping.getServletName().getValue());
		}
		
		if(servletMapping.getUrlPattern()!=null){
			servletMappingTmp.setUrlPattern(populateUrlPattern(servletMapping.getUrlPattern()));
		}
		
		return servletMappingTmp;
	}

	private List<com.sun.grizzly.http.webxml.schema.Taglib> populateTaglib(List<TaglibType> list){
		
		if(list==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.Taglib> taglibListTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.Taglib>(list.size());
		for (TaglibType item : list) {
			taglibListTmp.add(getTaglib(item));
		}
		
		return taglibListTmp;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.JspPropertyGroup> populateJspPropertyGroup(List<JspPropertyGroupType> list){
		
		if(list==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.JspPropertyGroup> groupListTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.JspPropertyGroup>(list.size());
		for (JspPropertyGroupType item : list) {
			groupListTmp.add(getJspPropertyGroup(item));
		}
		
		return groupListTmp;
	}

	private List<com.sun.grizzly.http.webxml.schema.WebResourceCollection> populateWebResourceCollection(List<WebResourceCollectionType> webResourceCollectionList){

		if(webResourceCollectionList==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.WebResourceCollection> webResourceCollectionListTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.WebResourceCollection>(webResourceCollectionList.size());
		for (WebResourceCollectionType res : webResourceCollectionList) {
			webResourceCollectionListTmp.add(getWebResourceCollection(res));
		}
		
		return webResourceCollectionListTmp;
	}

	private com.sun.grizzly.http.webxml.schema.SecurityRole getSecurityRole(SecurityRoleType securityRole){
		
		if(securityRole==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.SecurityRole securityRoleTmp = new com.sun.grizzly.http.webxml.schema.SecurityRole();
		
		if(securityRole.getRoleName()!=null){
			securityRoleTmp.setRoleName(securityRole.getRoleName().getValue());
		}
		if(securityRole.getDescription()!=null && securityRole.getDescription().size()>0){
			securityRoleTmp.setDescription(populateDescription(securityRole.getDescription()));
		}
		
		return securityRoleTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.RunAs getRunAs(RunAsType runAs){
		
		if(runAs==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.RunAs runAsTmp = new com.sun.grizzly.http.webxml.schema.RunAs();
		
		if(runAs.getRoleName()!=null){
			runAsTmp.setRoleName(runAs.getRoleName().getValue());
		}
		if(runAs.getDescription()!=null && runAs.getDescription().size()>0){
			runAsTmp.setDescription(populateDescription(runAs.getDescription()));
		}
		
		return runAsTmp;
	}

	private com.sun.grizzly.http.webxml.schema.Taglib getTaglib(TaglibType taglib){
		
		if(taglib==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.Taglib tagLibTmp = new com.sun.grizzly.http.webxml.schema.Taglib();
		
		if(taglib.getTaglibUri()!=null){
			tagLibTmp.setTaglibUri(taglib.getTaglibUri().getValue());
		}
		if(taglib.getTaglibLocation()!=null){
			tagLibTmp.setTaglibLocation(taglib.getTaglibLocation().getValue());
		}
		
		return tagLibTmp;
	}

	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.SecurityRole> populateSecurityRole(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("SecurityRoleType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("SecurityRoleType");
		
		List<com.sun.grizzly.http.webxml.schema.SecurityRole> securityRoleList = new ArrayList<com.sun.grizzly.http.webxml.schema.SecurityRole>(list.size());
		for (JAXBElement obj : list) {
			SecurityRoleType role = (SecurityRoleType)obj.getValue();
			securityRoleList.add(getSecurityRole(role));
		}
		
		return securityRoleList;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.ResourceRef> populateResourceRef(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("ResourceRefType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("ResourceRefType");
		
		List<com.sun.grizzly.http.webxml.schema.ResourceRef> resourceRefList = new ArrayList<com.sun.grizzly.http.webxml.schema.ResourceRef>(list.size());
		for (JAXBElement obj : list) {
			ResourceRefType resource = (ResourceRefType)obj.getValue();
			resourceRefList.add(getResourceRef(resource));
		}
		
		return resourceRefList;
	}

	private com.sun.grizzly.http.webxml.schema.LoginConfig getLoginConfig(LoginConfigType loginConfig){
		
		if(loginConfig==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.LoginConfig loginConfigTmp = new com.sun.grizzly.http.webxml.schema.LoginConfig();
		
		if(loginConfig.getAuthMethod()!=null){
			loginConfigTmp.setAuthMethod(loginConfig.getAuthMethod().getValue());
		}
		if(loginConfig.getFormLoginConfig()!=null){
			loginConfigTmp.setFormLoginConfig(new com.sun.grizzly.http.webxml.schema.FormLoginConfig(loginConfig.getFormLoginConfig().getFormLoginPage().getValue(),loginConfig.getFormLoginConfig().getFormErrorPage().getValue()));
		}
		if(loginConfig.getRealmName()!=null){
			loginConfigTmp.setRealmName(loginConfig.getRealmName().getValue());
		}
		
		return loginConfigTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.WelcomeFileList getWelcomeFileList(WelcomeFileListType welcomeFileList){
		
		if(welcomeFileList==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.WelcomeFileList welcomeFileTmp = new com.sun.grizzly.http.webxml.schema.WelcomeFileList();
		
		if(welcomeFileList.getWelcomeFile()!=null){
			welcomeFileTmp.setWelcomeFile(welcomeFileList.getWelcomeFile());
		}
		
		return welcomeFileTmp;
	}
	
	@SuppressWarnings("unchecked")
	private List<com.sun.grizzly.http.webxml.schema.ContextParam> populateContextParam(Map<String, List<JAXBElement<?>>> itemMap){
		
		if(!itemMap.containsKey("ParamValueType")){
			return null;
		}
		
		List<JAXBElement<?>> list = (List<JAXBElement<?>>)itemMap.get("ParamValueType");
		
		List<com.sun.grizzly.http.webxml.schema.ContextParam> contextParamList = new ArrayList<com.sun.grizzly.http.webxml.schema.ContextParam>(list.size());
		for (JAXBElement obj : list) {
			ParamValueType contextParam = (ParamValueType)obj.getValue();
			contextParamList.add(getContextParam(contextParam));
		}
		
		return contextParamList;
	}
	
	private com.sun.grizzly.http.webxml.schema.ResourceEnvRef getResourceEnvRef(ResourceEnvRefType resourceEnvRef){
		
		if(resourceEnvRef==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ResourceEnvRef resourceEnvRefTmp = new com.sun.grizzly.http.webxml.schema.ResourceEnvRef();
		
		if(resourceEnvRef.getResourceEnvRefName()!=null){
			resourceEnvRefTmp.setResourceEnvRefName(resourceEnvRef.getResourceEnvRefName().getValue());
		}
		if(resourceEnvRef.getResourceEnvRefType()!=null){
			resourceEnvRefTmp.setResourceEnvRefType(resourceEnvRef.getResourceEnvRefType().getValue());
		}
		if(resourceEnvRef.getDescription()!=null && resourceEnvRef.getDescription().size()>0){
			resourceEnvRefTmp.setDescription(populateDescription(resourceEnvRef.getDescription()));
		}
		if(resourceEnvRef.getMappedName()!=null){
			resourceEnvRefTmp.setMappedName(resourceEnvRef.getMappedName().getValue());
		}
		if(resourceEnvRef.getInjectionTarget()!=null && resourceEnvRef.getInjectionTarget().size()>0){
			resourceEnvRefTmp.setInjectionTarget(populateInjectionTarget(resourceEnvRef.getInjectionTarget()));
		}
		
		return resourceEnvRefTmp;
	}

	private com.sun.grizzly.http.webxml.schema.ContextParam getContextParam(ParamValueType contextParam){
		
		if(contextParam==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ContextParam contextParamTmp = new com.sun.grizzly.http.webxml.schema.ContextParam();
		
		if(contextParam.getParamName()!=null){
			contextParamTmp.setParamName(contextParam.getParamName().getValue());
		}
		if(contextParam.getParamValue()!=null){
			contextParamTmp.setParamValue(contextParam.getParamValue().getValue());
		}
		if(contextParam.getDescription()!=null && contextParam.getDescription().size()>0){
			contextParamTmp.setDescription(populateDescription(contextParam.getDescription()));
		}
		
		return contextParamTmp;
	}
	
	
	
}
