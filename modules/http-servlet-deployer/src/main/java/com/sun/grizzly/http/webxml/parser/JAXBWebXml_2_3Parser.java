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
import javax.xml.bind.Unmarshaller;
import com.sun.grizzly.http.webxml.schema.version_2_3.AuthConstraint;
import com.sun.grizzly.http.webxml.schema.version_2_3.ContextParam;
import com.sun.grizzly.http.webxml.schema.version_2_3.EjbLocalRef;
import com.sun.grizzly.http.webxml.schema.version_2_3.EjbRef;
import com.sun.grizzly.http.webxml.schema.version_2_3.EnvEntry;
import com.sun.grizzly.http.webxml.schema.version_2_3.ErrorCode;
import com.sun.grizzly.http.webxml.schema.version_2_3.ErrorPage;
import com.sun.grizzly.http.webxml.schema.version_2_3.ExceptionType;
import com.sun.grizzly.http.webxml.schema.version_2_3.Filter;
import com.sun.grizzly.http.webxml.schema.version_2_3.FilterMapping;
import com.sun.grizzly.http.webxml.schema.version_2_3.HttpMethod;
import com.sun.grizzly.http.webxml.schema.version_2_3.Icon;
import com.sun.grizzly.http.webxml.schema.version_2_3.InitParam;
import com.sun.grizzly.http.webxml.schema.version_2_3.JspFile;
import com.sun.grizzly.http.webxml.schema.version_2_3.Listener;
import com.sun.grizzly.http.webxml.schema.version_2_3.LoginConfig;
import com.sun.grizzly.http.webxml.schema.version_2_3.MimeMapping;
import com.sun.grizzly.http.webxml.schema.version_2_3.ResourceEnvRef;
import com.sun.grizzly.http.webxml.schema.version_2_3.ResourceRef;
import com.sun.grizzly.http.webxml.schema.version_2_3.RoleName;
import com.sun.grizzly.http.webxml.schema.version_2_3.SecurityConstraint;
import com.sun.grizzly.http.webxml.schema.version_2_3.SecurityRole;
import com.sun.grizzly.http.webxml.schema.version_2_3.SecurityRoleRef;
import com.sun.grizzly.http.webxml.schema.version_2_3.Servlet;
import com.sun.grizzly.http.webxml.schema.version_2_3.ServletClass;
import com.sun.grizzly.http.webxml.schema.version_2_3.ServletMapping;
import com.sun.grizzly.http.webxml.schema.version_2_3.ServletName;
import com.sun.grizzly.http.webxml.schema.version_2_3.SessionConfig;
import com.sun.grizzly.http.webxml.schema.version_2_3.Taglib;
import com.sun.grizzly.http.webxml.schema.version_2_3.UrlPattern;
import com.sun.grizzly.http.webxml.schema.version_2_3.WebApp;
import com.sun.grizzly.http.webxml.schema.version_2_3.WebResourceCollection;
import com.sun.grizzly.http.webxml.schema.version_2_3.WelcomeFile;
import com.sun.grizzly.http.webxml.schema.version_2_3.WelcomeFileList;


public class JAXBWebXml_2_3Parser implements IJAXBWebXmlParser {
	
	Map<String, List<Object>> itemMap = new HashMap<String, List<Object>>();
	
	public com.sun.grizzly.http.webxml.schema.WebApp parse(String webxml) throws Exception {
		
		JAXBContext jc = JAXBContext.newInstance("com.sun.grizzly.http.webxml.schema.version_2_3");
        
        // create an Unmarshaller
        Unmarshaller u = jc.createUnmarshaller();
        
        WebApp root = (WebApp)u.unmarshal(new FileInputStream(webxml));
        
        com.sun.grizzly.http.webxml.schema.WebApp webApp = populate(root);
        
        return webApp;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.Servlet> populateServlet(List<Servlet> listServlet){
		
		if(listServlet==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.Servlet> servletList = new ArrayList<com.sun.grizzly.http.webxml.schema.Servlet>();
		
		for (Servlet servlet : listServlet) {
			
			com.sun.grizzly.http.webxml.schema.Servlet servletTmp = new com.sun.grizzly.http.webxml.schema.Servlet();
			
			if(servlet.getIcon()!=null){
				List<com.sun.grizzly.http.webxml.schema.Icon> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.Icon>(1);
				listTmp.add(getIcon(servlet.getIcon()));
				servletTmp.setIcon(listTmp);
			}
			if(servlet.getDescription()!=null){
				List<String> list = new ArrayList<String>(1);
				list.add(servlet.getDescription().getvalue());
				servletTmp.setDescription(list);
			}
			if(servlet.getDisplayName()!=null){
				List<String> list = new ArrayList<String>(1);
				list.add(servlet.getDisplayName().getvalue());
				servletTmp.setDisplayName(list);
			}
			if(servlet.getServletName()!=null){
				servletTmp.setServletName(servlet.getServletName().getvalue());
			}
			if(servlet.getLoadOnStartup()!=null){
				servletTmp.setLoadOnStartup(servlet.getLoadOnStartup().getvalue());
			}
			
			List<InitParam> initParams = servlet.getInitParam();
			
			if(initParams!=null){
				List<com.sun.grizzly.http.webxml.schema.InitParam> initParamsTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.InitParam>(initParams.size());
				for (InitParam initParam : initParams) {
					initParamsTmp.add(getInitParam(initParam));
				}
				
				servletTmp.setInitParam(initParamsTmp);
			}
			
			if(servlet.getServletClassOrJspFile()!=null){
				
				Object object = servlet.getServletClassOrJspFile().get(0);
				if(object instanceof ServletClass){
					servletTmp.setServletClass(((ServletClass)object).getvalue());
				} else if(object instanceof JspFile){
					servletTmp.setJspFile(((JspFile)object).getvalue());
				}
				
			}
			
			List<SecurityRoleRef> securityRoleRefList = servlet.getSecurityRoleRef();
			
			if(securityRoleRefList!=null){
				List<com.sun.grizzly.http.webxml.schema.SecurityRoleRef> securityRoleRefTmpList = new ArrayList<com.sun.grizzly.http.webxml.schema.SecurityRoleRef>(securityRoleRefList.size());
				for (SecurityRoleRef securityRoleRef : securityRoleRefList) {
					securityRoleRefTmpList.add(getSecurityRoleRef(securityRoleRef));
				}
				
				servletTmp.setSecurityRoleRef(securityRoleRefTmpList);
			}
		
			servletList.add(servletTmp);
		}
		
		return servletList;
	}
	
	private com.sun.grizzly.http.webxml.schema.WebApp populate(WebApp root){
		
		com.sun.grizzly.http.webxml.schema.WebApp webApp = new com.sun.grizzly.http.webxml.schema.WebApp();
		
		if(root.getDisplayName()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(root.getDisplayName().getvalue());
			
			webApp.setDisplayName(listTmp);
		}
		if(root.getDescription()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(root.getDescription().getvalue());
			
			webApp.setDescription(listTmp);
		}
		if(root.getIcon()!=null){
			List<com.sun.grizzly.http.webxml.schema.Icon> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.Icon>(1);
			listTmp.add(getIcon(root.getIcon()));
			
			webApp.setIcon(listTmp);
		}
		if(root.getDistributable()!=null){
			webApp.setDistributable(true);
		}
		
		// force metadata-complete to true : Annotation only supported servlet 2.5+
		webApp.setMetadataComplete(true);
		
		webApp.setServlet(populateServlet(root.getServlet()));
		webApp.setServletMapping(populateServletMapping(root.getServletMapping()));
		webApp.setFilter(populateFilter(root.getFilter()));
		webApp.setFilterMapping(populateFilterMapping(root.getFilterMapping()));
		webApp.setContextParam(populateContextParam(root.getContextParam()));
		webApp.setEjbLocalRef(populateEjbLocalRef(root.getEjbLocalRef()));
		webApp.setEjbRef(populateEjbRef(root.getEjbRef()));
		webApp.setEnvEntry(populateEnvEntry(root.getEnvEntry()));
		webApp.setErrorPage(populateErrorPage(root.getErrorPage()));
		webApp.setListener(populateListener(root.getListener()));
		webApp.setLoginConfig(populateLoginConfig(root.getLoginConfig()));
		webApp.setMimeMapping(populateMimeMapping(root.getMimeMapping()));
		webApp.setResourceRef(populateResourceRef(root.getResourceRef()));
		webApp.setResourceEnvRef(populateResourceEnvRef(root.getResourceEnvRef()));
		webApp.setSecurityConstraint(populateSecurityConstraint(root.getSecurityConstraint()));
		webApp.setSecurityRole(populateSecurityRole(root.getSecurityRole()));
		webApp.setSessionConfig(populateSessionConfig(root.getSessionConfig()));
		webApp.setTaglib(populateTaglib(root.getTaglib()));
		webApp.setWelcomeFileList(populateWelcomeFileList(root.getWelcomeFileList()));
		
		return webApp;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.SecurityConstraint> populateSecurityConstraint(List<SecurityConstraint> securityConstraint){
		
		if(securityConstraint==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.SecurityConstraint> securityConstraintList = new ArrayList<com.sun.grizzly.http.webxml.schema.SecurityConstraint>(securityConstraint.size());
		for (SecurityConstraint security : securityConstraint) {
			securityConstraintList.add(getSecurityConstraint(security));
		}
		
		return securityConstraintList;
	}
	
	private com.sun.grizzly.http.webxml.schema.SecurityConstraint getSecurityConstraint(SecurityConstraint securityConstraint){
		
		if(securityConstraint==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.SecurityConstraint securityConstraintTmp = new com.sun.grizzly.http.webxml.schema.SecurityConstraint();
		
		if(securityConstraint.getAuthConstraint()!=null){
			securityConstraintTmp.setAuthConstraint(getAuthConstraint(securityConstraint.getAuthConstraint()));
		}
		if(securityConstraint.getDisplayName()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(securityConstraint.getDisplayName().getvalue());
			
			securityConstraintTmp.setDisplayName(listTmp);
		}
		if(securityConstraint.getUserDataConstraint()!=null){
			
			com.sun.grizzly.http.webxml.schema.UserDataConstraint userData =  new com.sun.grizzly.http.webxml.schema.UserDataConstraint();
			
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(securityConstraint.getUserDataConstraint().getDescription().getvalue());
			
			userData.setDescription(listTmp);
			userData.setTransportGuarantee(securityConstraint.getUserDataConstraint().getTransportGuarantee().getvalue());
			
			securityConstraintTmp.setUserDataConstraint(userData);
		}
		if(securityConstraint.getWebResourceCollection()!=null){
			securityConstraintTmp.setWebResourceCollection(populateWebResourceCollection(securityConstraint.getWebResourceCollection()));
		}
		
		return securityConstraintTmp;
	}
	
	protected List<com.sun.grizzly.http.webxml.schema.LoginConfig> populateLoginConfig(LoginConfig loginConfig){
		
		if(loginConfig==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.LoginConfig> list = new ArrayList<com.sun.grizzly.http.webxml.schema.LoginConfig>();
		
		com.sun.grizzly.http.webxml.schema.LoginConfig loginConfigTmp = new com.sun.grizzly.http.webxml.schema.LoginConfig();
		
		if(loginConfig.getAuthMethod()!=null){
			loginConfigTmp.setAuthMethod(loginConfig.getAuthMethod().getvalue());
		}
		if(loginConfig.getFormLoginConfig()!=null){
			loginConfigTmp.setFormLoginConfig(new com.sun.grizzly.http.webxml.schema.FormLoginConfig(loginConfig.getFormLoginConfig().getFormLoginPage().getvalue(),loginConfig.getFormLoginConfig().getFormErrorPage().getvalue()));
		}
		if(loginConfig.getRealmName()!=null){
			loginConfigTmp.setRealmName(loginConfig.getRealmName().getvalue());
		}
		
		list.add(loginConfigTmp);
		return list;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.FilterMapping> populateFilterMapping(List<FilterMapping> filterMapping){
		
		if(filterMapping==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.FilterMapping> filterMappingList = new ArrayList<com.sun.grizzly.http.webxml.schema.FilterMapping>(filterMapping.size());
		for (FilterMapping mapping : filterMapping) {
			filterMappingList.add(getFilterMapping(mapping));
		}
		
		return filterMappingList;
	}

	private List<com.sun.grizzly.http.webxml.schema.EnvEntry> populateEnvEntry(List<EnvEntry> envEntry){
		
		if(envEntry==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.EnvEntry> envEntryList = new ArrayList<com.sun.grizzly.http.webxml.schema.EnvEntry>(envEntry.size());
		for (EnvEntry env : envEntry) {
			envEntryList.add(getEnvEntry(env));
		}
		
		return envEntryList;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.EjbLocalRef> populateEjbLocalRef(List<EjbLocalRef> ejbLocalRef){
		
		if(ejbLocalRef==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.EjbLocalRef> ejbLocalRefList = new ArrayList<com.sun.grizzly.http.webxml.schema.EjbLocalRef>(ejbLocalRef.size());
		for (EjbLocalRef ejb : ejbLocalRef) {
			ejbLocalRefList.add(getEjbLocalRef(ejb));
		}
		
		return ejbLocalRefList;
	}
	
	private com.sun.grizzly.http.webxml.schema.EjbLocalRef getEjbLocalRef(EjbLocalRef ejb){
		if(ejb==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.EjbLocalRef ejbLocalRefTmp = new com.sun.grizzly.http.webxml.schema.EjbLocalRef();
		
		if(ejb.getEjbLink()!=null){
			ejbLocalRefTmp.setEjbLink(ejb.getEjbLink().getvalue());
		}
		if(ejb.getEjbRefName()!=null){
			ejbLocalRefTmp.setEjbRefName(ejb.getEjbRefName().getvalue());
		}
		if(ejb.getEjbRefType()!=null){
			ejbLocalRefTmp.setEjbRefType(ejb.getEjbRefType().getvalue());
		}
		if(ejb.getDescription()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(ejb.getDescription().getvalue());
			ejbLocalRefTmp.setDescription(listTmp);
		}
		if(ejb.getLocal()!=null){
			ejbLocalRefTmp.setLocal(ejb.getLocal().getvalue());
		}
		if(ejb.getLocalHome()!=null){
			ejbLocalRefTmp.setLocalHome(ejb.getLocalHome().getvalue());
		}
		
		return ejbLocalRefTmp;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.Listener> populateListener(List<Listener> listener){
		
		if(listener==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.Listener> contextParamList = new ArrayList<com.sun.grizzly.http.webxml.schema.Listener>(listener.size());
		for (Listener item : listener) {
			contextParamList.add(getListener(item));
		}
		
		return contextParamList;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.Filter> populateFilter(List<Filter> filters){
		
		if(filters==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.Filter> filterList = new ArrayList<com.sun.grizzly.http.webxml.schema.Filter>(filters.size());
		for (Filter filter : filters) {
			
			com.sun.grizzly.http.webxml.schema.Filter filterTmp = new com.sun.grizzly.http.webxml.schema.Filter();
			
			if(filter.getIcon()!=null){
				List<com.sun.grizzly.http.webxml.schema.Icon> listTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.Icon>(1);
				listTmp.add(getIcon(filter.getIcon()));
				filterTmp.setIcon(listTmp);
			}
			if(filter.getDescription()!=null){
				List<String> listTmp = new ArrayList<String>(1);
				listTmp.add(filter.getDescription().getvalue());
				filterTmp.setDescription(listTmp);
			}
			if(filter.getDisplayName()!=null){
				List<String> listTmp = new ArrayList<String>(1);
				listTmp.add(filter.getDisplayName().getvalue());
				filterTmp.setDisplayName(listTmp);
			}
			if(filter.getFilterName()!=null){
				filterTmp.setFilterName(filter.getFilterName().getvalue());
			}
			if(filter.getFilterClass()!=null){
				filterTmp.setFilterClass(filter.getFilterClass().getvalue());
			}
			
			List<InitParam> initParams = filter.getInitParam();
			
			if(initParams!=null){
				List<com.sun.grizzly.http.webxml.schema.InitParam> initParamsTmp = new ArrayList<com.sun.grizzly.http.webxml.schema.InitParam>(initParams.size());
				for (InitParam initParam : initParams) {
					initParamsTmp.add(getInitParam(initParam));
				}
				
				filterTmp.setInitParam(initParamsTmp);
			}
			
		}
		
		return filterList;
	}
	
	private com.sun.grizzly.http.webxml.schema.InitParam getInitParam(InitParam initParam){
		
		if(initParam==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.InitParam initParamTmp = new com.sun.grizzly.http.webxml.schema.InitParam();
		
		if(initParam.getParamName()!=null){
			initParamTmp.setParamName(initParam.getParamName().getvalue());
		}
		if(initParam.getParamValue()!=null){
			initParamTmp.setParamValue(initParam.getParamValue().getvalue());
		}
		if(initParam.getDescription()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(initParam.getDescription().getvalue());
			initParamTmp.setDescription(listTmp);
		}
		
		return initParamTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.FilterMapping getFilterMapping(FilterMapping filterMapping){
		
		if(filterMapping==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.FilterMapping filterMappingTmp = new com.sun.grizzly.http.webxml.schema.FilterMapping();
		
		if(filterMapping.getFilterName()!=null){
			filterMappingTmp.setFilterName(filterMapping.getFilterName().getvalue());
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
		
		return filterMappingTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.Listener getListener(Listener listener){
		
		if(listener==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.Listener listenerTmp = new com.sun.grizzly.http.webxml.schema.Listener();
		
		if(listener.getListenerClass()!=null){
			listenerTmp.setListenerClass(listener.getListenerClass().getvalue());
		}

		return listenerTmp;
	}
	
	private com.sun.grizzly.http.webxml.schema.SecurityRoleRef getSecurityRoleRef(SecurityRoleRef securityRoleRef){
		
		if(securityRoleRef==null){
			return null;
		}
		com.sun.grizzly.http.webxml.schema.SecurityRoleRef srf = new com.sun.grizzly.http.webxml.schema.SecurityRoleRef();
		
		if(securityRoleRef.getRoleName()!=null){
			srf.setRoleName(securityRoleRef.getRoleName().getvalue());
		}
		if(securityRoleRef.getRoleLink()!=null){
			srf.setRoleLink(securityRoleRef.getRoleLink().getvalue());
		}
		if(securityRoleRef.getDescription()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(securityRoleRef.getDescription().getvalue());
			srf.setDescription(listTmp);
		}
		
		return srf;
		
		
		
	}
	
	private com.sun.grizzly.http.webxml.schema.Icon getIcon(Icon icon){
		
		if(icon==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.Icon iconTmp = new com.sun.grizzly.http.webxml.schema.Icon();
		
		if(icon.getSmallIcon()!=null){
			iconTmp.setSmallIcon(icon.getSmallIcon().getvalue());
		}
		if(icon.getLargeIcon()!=null){
			iconTmp.setLargeIcon(icon.getLargeIcon().getvalue());
		}
		
		return iconTmp;
		
	}

	private List<com.sun.grizzly.http.webxml.schema.EjbRef> populateEjbRef(List<EjbRef> ejbRef){
		
		if(ejbRef==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.EjbRef> ejbRefList = new ArrayList<com.sun.grizzly.http.webxml.schema.EjbRef>(ejbRef.size());
		for (EjbRef ejb : ejbRef) {
			ejbRefList.add(getEjbRef(ejb));
		}
		
		return ejbRefList;
	}

	private com.sun.grizzly.http.webxml.schema.EjbRef getEjbRef(EjbRef ejb){
		if(ejb==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.EjbRef ejbRefTmp = new com.sun.grizzly.http.webxml.schema.EjbRef();
		
		if(ejb.getEjbLink()!=null){
			ejbRefTmp.setEjbLink(ejb.getEjbLink().getvalue());
		}
		if(ejb.getEjbRefName()!=null){
			ejbRefTmp.setEjbRefName(ejb.getEjbRefName().getvalue());
		}
		if(ejb.getEjbRefType()!=null){
			ejbRefTmp.setEjbRefType(ejb.getEjbRefType().getvalue());
		}
		if(ejb.getDescription()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(ejb.getDescription().getvalue());
			ejbRefTmp.setDescription(listTmp);
		}
		if(ejb.getEjbLink()!=null){
			ejbRefTmp.setEjbLink(ejb.getEjbLink().getvalue());
		}
		if(ejb.getHome()!=null){
			ejbRefTmp.setHome(ejb.getHome().getvalue());
		}
		if(ejb.getRemote()!=null){
			ejbRefTmp.setRemote(ejb.getRemote().getvalue());
		}
		
		return ejbRefTmp;
	}

	private com.sun.grizzly.http.webxml.schema.EnvEntry getEnvEntry(EnvEntry envEntry){
		
		if(envEntry==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.EnvEntry envEntryTmp = new com.sun.grizzly.http.webxml.schema.EnvEntry();
		
		if(envEntry.getEnvEntryName()!=null){
			envEntryTmp.setEnvEntryName(envEntry.getEnvEntryName().getvalue());
		}
		if(envEntry.getEnvEntryType()!=null){
			envEntryTmp.setEnvEntryType(envEntry.getEnvEntryType().getvalue());
		}
		if(envEntry.getEnvEntryValue()!=null){
			envEntryTmp.setEnvEntryValue(envEntry.getEnvEntryValue().getvalue());
		}
		if(envEntry.getDescription()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(envEntry.getDescription().getvalue());
			envEntryTmp.setDescription(listTmp);
		}
		
		return envEntryTmp;
	}

	private com.sun.grizzly.http.webxml.schema.ErrorPage getErrorPage(ErrorPage errorPage){
		
		if(errorPage==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ErrorPage errorPageTmp = new com.sun.grizzly.http.webxml.schema.ErrorPage();
		
		if(errorPage.getLocation()!=null){
			errorPageTmp.setLocation(errorPage.getLocation().getvalue());
		}
		if(errorPage.getErrorCodeOrExceptionType()!=null){
			Object object = errorPage.getErrorCodeOrExceptionType().get(0);
			if(object instanceof ErrorCode){
				errorPageTmp.setErrorCode(((ErrorCode)object).getvalue());
			} else if(object instanceof ExceptionType){
				errorPageTmp.setExceptionType(((ExceptionType)object).getvalue());
			}
				
		}
		
		return errorPageTmp;
	}

	private List<com.sun.grizzly.http.webxml.schema.ErrorPage> populateErrorPage(List<ErrorPage> errorPage){
		
		if(errorPage==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.ErrorPage> errorPageList = new ArrayList<com.sun.grizzly.http.webxml.schema.ErrorPage>(errorPage.size());
		for (ErrorPage page : errorPage) {
			errorPageList.add(getErrorPage(page));
		}
		
		return errorPageList;
	}

	private List<com.sun.grizzly.http.webxml.schema.MimeMapping> populateMimeMapping(List<MimeMapping> mimeMapping){
		
		if(mimeMapping==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.MimeMapping> mimeMappingList = new ArrayList<com.sun.grizzly.http.webxml.schema.MimeMapping>(mimeMapping.size());
		for (MimeMapping mapping : mimeMapping) {
			mimeMappingList.add(getMimeMapping(mapping));
		}
		
		return mimeMappingList;
	}

	private com.sun.grizzly.http.webxml.schema.MimeMapping getMimeMapping(MimeMapping mimeMapping){
		
		if(mimeMapping==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.MimeMapping mimeMappingTmp = new com.sun.grizzly.http.webxml.schema.MimeMapping();
		
		if(mimeMapping.getExtension()!=null){
			mimeMappingTmp.setExtension(mimeMapping.getExtension().getvalue());
		}
		if(mimeMapping.getMimeType()!=null){
			mimeMappingTmp.setMimeType(mimeMapping.getMimeType().getvalue());
		}
		
		return mimeMappingTmp;
	}

	private List<com.sun.grizzly.http.webxml.schema.ResourceEnvRef> populateResourceEnvRef(List<ResourceEnvRef> resourceEnvRef){
		
		if(resourceEnvRef==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.ResourceEnvRef> resourceEnvRefList = new ArrayList<com.sun.grizzly.http.webxml.schema.ResourceEnvRef>(resourceEnvRef.size());
		for (ResourceEnvRef res : resourceEnvRef) {
			resourceEnvRefList.add(getResourceEnvRef(res));
		}
		
		return resourceEnvRefList;
	}

	private com.sun.grizzly.http.webxml.schema.ResourceRef getResourceRef(ResourceRef resourceRef){
		
		if(resourceRef==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ResourceRef resourceRefTmp = new com.sun.grizzly.http.webxml.schema.ResourceRef();
		
		if(resourceRef.getResRefName()!=null){
			resourceRefTmp.setResRefName(resourceRef.getResRefName().getvalue());
		}
		if(resourceRef.getResAuth()!=null){
			resourceRefTmp.setResAuth(resourceRef.getResAuth().getvalue());
		}
		if(resourceRef.getResSharingScope()!=null){
			resourceRefTmp.setResSharingScope(resourceRef.getResSharingScope().getvalue());
		}
		if(resourceRef.getResType()!=null){
			resourceRefTmp.setResType(resourceRef.getResType().getvalue());
		}
		if(resourceRef.getDescription()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(resourceRef.getDescription().getvalue());
			resourceRefTmp.setDescription(listTmp);
		}
		
		return resourceRefTmp;
	}

	private com.sun.grizzly.http.webxml.schema.AuthConstraint getAuthConstraint(AuthConstraint authConstraint){
		
		if(authConstraint==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.AuthConstraint authConstraintTmp = new com.sun.grizzly.http.webxml.schema.AuthConstraint();
		
		if(authConstraint.getRoleName()!=null){
			
			List<RoleName> list = authConstraint.getRoleName();
			if(list!=null){
				
				List<String> roleList = new ArrayList<String>(list.size());
				for (RoleName roleName : list) {
					roleList.add(roleName.getvalue());
				}
				
				authConstraintTmp.setRoleName(roleList);
			}
			
		}
	
		if(authConstraint.getDescription()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(authConstraint.getDescription().getvalue());
			authConstraintTmp.setDescription(listTmp);
		}
		
		return authConstraintTmp;
	}

	private List<com.sun.grizzly.http.webxml.schema.ServletMapping> populateServletMapping(List<ServletMapping> servletMapping){
		
		if(servletMapping==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.ServletMapping> servletMappingList = new ArrayList<com.sun.grizzly.http.webxml.schema.ServletMapping>(servletMapping.size());
		for (ServletMapping mapping : servletMapping) {
			servletMappingList.add(getServletMapping(mapping));
		}
		
		return servletMappingList;
	}

	private com.sun.grizzly.http.webxml.schema.WebResourceCollection getWebResourceCollection(WebResourceCollection webResourceCollection){
		
		if(webResourceCollection==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.WebResourceCollection webResourceCollectionTmp = new com.sun.grizzly.http.webxml.schema.WebResourceCollection();
		
		if(webResourceCollection.getHttpMethod()!=null){
			List<HttpMethod> list = webResourceCollection.getHttpMethod();
			if(list!=null){
				
				List<String> httpMethodList = new ArrayList<String>(list.size());
				for (HttpMethod http : list) {
					httpMethodList.add(http.getvalue());
				}
				
				webResourceCollectionTmp.setHttpMethod(httpMethodList);
			}
		}
		if(webResourceCollection.getUrlPattern()!=null){
			List<UrlPattern> list = webResourceCollection.getUrlPattern();
			if(list!=null){
				
				List<String> urlPatternList = new ArrayList<String>(list.size());
				for (UrlPattern url : list) {
					urlPatternList.add(url.getvalue());
				}
				
				webResourceCollectionTmp.setUrlPattern(urlPatternList);
			}
		}
		if(webResourceCollection.getDescription()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(webResourceCollection.getDescription().getvalue());
			webResourceCollectionTmp.setDescription(listTmp);
		}
		
		return webResourceCollectionTmp;
	}

	protected List<com.sun.grizzly.http.webxml.schema.SessionConfig> populateSessionConfig(SessionConfig sessionConfig){
		
		if(sessionConfig==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.SessionConfig> list = new ArrayList<com.sun.grizzly.http.webxml.schema.SessionConfig>();
		com.sun.grizzly.http.webxml.schema.SessionConfig sessionConfigTmp = new com.sun.grizzly.http.webxml.schema.SessionConfig();
		
		if(sessionConfig.getSessionTimeout()!=null){
			sessionConfigTmp.setSessionTimeout(sessionConfig.getSessionTimeout().getvalue());
		}
		
		list.add(sessionConfigTmp);
		return list;
	}
	
	private com.sun.grizzly.http.webxml.schema.ServletMapping getServletMapping(ServletMapping servletMapping){
		
		if(servletMapping==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ServletMapping servletMappingTmp = new com.sun.grizzly.http.webxml.schema.ServletMapping();
		
		if(servletMapping.getServletName()!=null){
			servletMappingTmp.setServletName(servletMapping.getServletName().getvalue());
		}
		if(servletMapping.getUrlPattern()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(servletMapping.getUrlPattern().getvalue());
			servletMappingTmp.setUrlPattern(listTmp);
		}
		
		return servletMappingTmp;
	}

	private List<com.sun.grizzly.http.webxml.schema.Taglib> populateTaglib(List<Taglib> taglib){
		
		if(taglib==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.Taglib> taglibList = new ArrayList<com.sun.grizzly.http.webxml.schema.Taglib>(taglib.size());
		for (Taglib tag : taglib) {
			taglibList.add(getTaglib(tag));
		}
		
		return taglibList;
	}

	private List<com.sun.grizzly.http.webxml.schema.WebResourceCollection> populateWebResourceCollection(List<WebResourceCollection> webResourceCollection){
			
		if(webResourceCollection==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.WebResourceCollection> webResourceCollectionList = new ArrayList<com.sun.grizzly.http.webxml.schema.WebResourceCollection>(webResourceCollection.size());
		for (WebResourceCollection res : webResourceCollection) {
			webResourceCollectionList.add(getWebResourceCollection(res));
		}
		
		return webResourceCollectionList;
	}

	private com.sun.grizzly.http.webxml.schema.SecurityRole getSecurityRole(SecurityRole securityRole){
		
		if(securityRole==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.SecurityRole securityRoleTmp = new com.sun.grizzly.http.webxml.schema.SecurityRole();
		
		if(securityRole.getRoleName()!=null){
			securityRoleTmp.setRoleName(securityRole.getRoleName().getvalue());
		}
		if(securityRole.getDescription()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(securityRole.getDescription().getvalue());
			securityRoleTmp.setDescription(listTmp);
		}
		
		return securityRoleTmp;
	}

	private com.sun.grizzly.http.webxml.schema.Taglib getTaglib(Taglib tagLib){
		
		if(tagLib==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.Taglib tagLibTmp = new com.sun.grizzly.http.webxml.schema.Taglib();
		
		if(tagLib.getTaglibUri()!=null){
			tagLibTmp.setTaglibUri(tagLib.getTaglibUri().getvalue());
		}
		if(tagLib.getTaglibLocation()!=null){
			tagLibTmp.setTaglibLocation(tagLib.getTaglibLocation().getvalue());
		}
		
		return tagLibTmp;
	}

	private List<com.sun.grizzly.http.webxml.schema.SecurityRole> populateSecurityRole(List<SecurityRole> securityRole){
		
		if(securityRole==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.SecurityRole> securityRoleList = new ArrayList<com.sun.grizzly.http.webxml.schema.SecurityRole>(securityRole.size());
		for (SecurityRole role : securityRole) {
			securityRoleList.add(getSecurityRole(role));
		}
		
		return securityRoleList;
	}

	private List<com.sun.grizzly.http.webxml.schema.ResourceRef> populateResourceRef(List<ResourceRef> resourceRef){
		
		if(resourceRef==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.ResourceRef> resourceRefList = new ArrayList<com.sun.grizzly.http.webxml.schema.ResourceRef>(resourceRef.size());
		for (ResourceRef resource : resourceRef) {
			resourceRefList.add(getResourceRef(resource));
		}
		
		return resourceRefList;
	}

	private com.sun.grizzly.http.webxml.schema.ContextParam getContextParam(ContextParam contextParam){
		
		if(contextParam==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ContextParam contextParamTmp = new com.sun.grizzly.http.webxml.schema.ContextParam();
		
		if(contextParam.getParamName()!=null){
			contextParamTmp.setParamName(contextParam.getParamName().getvalue());
		}
		if(contextParam.getParamValue()!=null){
			contextParamTmp.setParamValue(contextParam.getParamValue().getvalue());
		}
		if(contextParam.getDescription()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(contextParam.getDescription().getvalue());
			contextParamTmp.setDescription(listTmp);
		}
		
		return contextParamTmp;
	}
	
	protected List<com.sun.grizzly.http.webxml.schema.WelcomeFileList> populateWelcomeFileList(WelcomeFileList welcomeFileList){
		
		if(welcomeFileList==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.WelcomeFileList> welcomelist = new ArrayList<com.sun.grizzly.http.webxml.schema.WelcomeFileList>();
		
		com.sun.grizzly.http.webxml.schema.WelcomeFileList welcomeFileTmp = new com.sun.grizzly.http.webxml.schema.WelcomeFileList();
		
		if(welcomeFileList.getWelcomeFile()!=null){
			List<WelcomeFile> list = welcomeFileList.getWelcomeFile();
			if(list!=null){
				
				List<String> welcomeFile = new ArrayList<String>(list.size());
				for (WelcomeFile file : list) {
					welcomeFile.add(file.getvalue());
				}
				
				welcomeFileTmp.setWelcomeFile(welcomeFile);
			}
		}
		
		welcomelist.add(welcomeFileTmp);
		return welcomelist;
	}
	
	private List<com.sun.grizzly.http.webxml.schema.ContextParam> populateContextParam(List<ContextParam> contextParams){
		
		if(contextParams==null){
			return null;
		}
		
		List<com.sun.grizzly.http.webxml.schema.ContextParam> contextParamList = new ArrayList<com.sun.grizzly.http.webxml.schema.ContextParam>(contextParams.size());
		for (ContextParam contextParam : contextParams) {
			contextParamList.add(getContextParam(contextParam));
		}
		
		return contextParamList;
	}
	
	private com.sun.grizzly.http.webxml.schema.ResourceEnvRef getResourceEnvRef(ResourceEnvRef resourceEnvRef){
		
		if(resourceEnvRef==null){
			return null;
		}
		
		com.sun.grizzly.http.webxml.schema.ResourceEnvRef resourceEnvRefTmp = new com.sun.grizzly.http.webxml.schema.ResourceEnvRef();
		
		if(resourceEnvRef.getResourceEnvRefName()!=null){
			resourceEnvRefTmp.setResourceEnvRefName(resourceEnvRef.getResourceEnvRefName().getvalue());
		}
		if(resourceEnvRef.getResourceEnvRefType()!=null){
			resourceEnvRefTmp.setResourceEnvRefType(resourceEnvRef.getResourceEnvRefType().getvalue());
		}
		if(resourceEnvRef.getDescription()!=null){
			List<String> listTmp = new ArrayList<String>(1);
			listTmp.add(resourceEnvRef.getDescription().getvalue());
			resourceEnvRefTmp.setDescription(listTmp);
		}
		
		return resourceEnvRefTmp;
	}
	
	
	
}
