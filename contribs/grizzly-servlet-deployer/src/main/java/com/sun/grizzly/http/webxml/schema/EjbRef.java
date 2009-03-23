package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class EjbRef {
	public List<String> description;  
    public String ejbRefName;
    public String ejbRefType;
    public String home;
    public String remote;
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
	public String getHome() {
		return home;
	}
	public void setHome(String home) {
		this.home = home;
	}
	public String getRemote() {
		return remote;
	}
	public void setRemote(String remote) {
		this.remote = remote;
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
		buffer.append("<EjbRef>").append("\n");
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
		buffer.append("<home>").append(home).append("</home>").append("\n");
		buffer.append("<remote>").append(remote).append("</remote>").append("\n");
		buffer.append("</EjbRef>");
		return buffer.toString();
	}
}


