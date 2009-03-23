package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class EjbLocalRef {
	public List<String> description;  
    public String ejbRefName;
    public String ejbRefType;
    public String localHome;
    public String local;
    public String ejbLink;
    public String mappedName;
    public List<InjectionTarget> injectionTarget;
    
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public String getEjbRefName() {
		return ejbRefName;
	}
	public void setEjbRefName(String ejbRefName) {
		this.ejbRefName = ejbRefName;
	}
	public String getEjbRefType() {
		return ejbRefType;
	}
	public void setEjbRefType(String ejbRefType) {
		this.ejbRefType = ejbRefType;
	}
	public String getLocalHome() {
		return localHome;
	}
	public void setLocalHome(String localHome) {
		this.localHome = localHome;
	}
	public String getLocal() {
		return local;
	}
	public void setLocal(String local) {
		this.local = local;
	}
	public String getEjbLink() {
		return ejbLink;
	}
	public void setEjbLink(String ejbLink) {
		this.ejbLink = ejbLink;
	}
	
	public String getMappedName() {
		return mappedName;
	}
	public void setMappedName(String mappedName) {
		this.mappedName = mappedName;
	}
	public List<InjectionTarget> getInjectionTarget() {
		return injectionTarget;
	}
	public void setInjectionTarget(List<InjectionTarget> injectionTarget) {
		this.injectionTarget = injectionTarget;
	}
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<EjbLocalRef>").append("\n");
		if(description!=null && description.size()>0){
			List<String> list = description;
			
			for (String item : list) {
				buffer.append("<description>").append(item).append("</description>").append("\n");
			}
		} 
		buffer.append("<mappedName>").append(mappedName).append("</mappedName>").append("\n");
		
		if(injectionTarget!=null && injectionTarget.size()>0){
			List<InjectionTarget> list = injectionTarget;
			
			for (InjectionTarget item : list) {
				buffer.append(item).append("\n");
			}
		} 
		buffer.append("<ejbLink>").append(ejbLink).append("</ejbLink>").append("\n");
		buffer.append("<ejbRefName>").append(ejbRefName).append("</ejbRefName>").append("\n");
		buffer.append("<ejbRefType>").append(ejbRefType).append("</ejbRefType>").append("\n");
		buffer.append("<local>").append(local).append("</local>").append("\n");
		buffer.append("<localHome>").append(localHome).append("</localHome>").append("\n");
		buffer.append("</EjbLocalRef>");
		return buffer.toString();
	}
}
