package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class Servlet {
	
	public List<Icon> icon;
	public String servletName;
	public List<String> displayName;  
	public List<String> description;  
    public String servletClass;
    public String jspFile;
    public List<InitParam> initParam;
    public String loadOnStartup;
    public RunAs runAs;
    public List<SecurityRoleRef> securityRoleRef;
    public boolean asyncSupported;
    public String asyncTimeout;

	public List<Icon> getIcon() {
		return icon;
	}
	public void setIcon(List<Icon> icon) {
		this.icon = icon;
	}
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public String getServletName() {
		return servletName;
	}
	public void setServletName(String servletName) {
		this.servletName = servletName;
	}
	public List<String> getDisplayName() {
		return displayName;
	}
	public void setDisplayName(List<String> displayName) {
		this.displayName = displayName;
	}
	public String getLoadOnStartup() {
		return loadOnStartup;
	}
	public void setLoadOnStartup(String loadOnStartup) {
		this.loadOnStartup = loadOnStartup;
	}
	public List<InitParam> getInitParam() {
		return initParam;
	}
	public void setInitParam(List<InitParam> initParam) {
		this.initParam = initParam;
	}
	public String getServletClass() {
		return servletClass;
	}
	public void setServletClass(String servletClass) {
		this.servletClass = servletClass;
	}
	public String getJspFile() {
		return jspFile;
	}
	public void setJspFile(String jspFile) {
		this.jspFile = jspFile;
	}
	public List<SecurityRoleRef> getSecurityRoleRef() {
		return securityRoleRef;
	}
	public void setSecurityRoleRef(List<SecurityRoleRef> securityRoleRef) {
		this.securityRoleRef = securityRoleRef;
	}
	public RunAs getRunAs() {
		return runAs;
	}
	public void setRunAs(RunAs runAs) {
		this.runAs = runAs;
	}
	public boolean isAsyncSupported() {
		return asyncSupported;
	}
	public void setAsyncSupported(boolean asyncSupported) {
		this.asyncSupported = asyncSupported;
	}
	public String getAsyncTimeout() {
		return asyncTimeout;
	}
	public void setAsyncTimeout(String asyncTimeout) {
		this.asyncTimeout = asyncTimeout;
	}
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<Servlet>").append("\n");
		if(description!=null && description.size()>0){
			List<String> list = description;
			
			for (String item : list) {
				buffer.append("<description>").append(item).append("</description>").append("\n");
			}
		} 
		if(displayName!=null && displayName.size()>0){
			List<String> list = displayName;
			
			for (String item : list) {
				buffer.append("<displayName>").append(item).append("</displayName>").append("\n");
			}
		} 
		
		if(icon!=null && icon.size()>0){
			List<Icon> list = icon;
			
			for (Icon item : list) {
				buffer.append(item).append("\n");
			}
		} 
		buffer.append("<servletName>").append(servletName).append("</servletName>").append("\n");
		buffer.append("<servletClass>").append(servletClass).append("</servletClass>").append("\n");
		buffer.append("<asyncSupported>").append(asyncSupported).append("</asyncSupported>").append("\n");
		buffer.append("<asyncTimeout>").append(asyncTimeout).append("</asyncTimeout>").append("\n");
		buffer.append("<jspFile>").append(jspFile).append("</jspFile>").append("\n");
		buffer.append("<runAs>").append(runAs).append("</runAs>").append("\n");
		
		if(initParam!=null && initParam.size()>0){
			List<InitParam> list = initParam;
			
			for (InitParam item : list) {
				buffer.append(item).append("\n");
			}
		}
		
		if(securityRoleRef!=null && securityRoleRef.size()>0){
			List<SecurityRoleRef> list = securityRoleRef;
			
			for (SecurityRoleRef item : list) {
				buffer.append(item).append("\n");
			}
		}

		buffer.append("<loadOnStartup>").append(loadOnStartup).append("</loadOnStartup>").append("\n");
		buffer.append("</Servlet>");
		return buffer.toString();
	}
	
}
