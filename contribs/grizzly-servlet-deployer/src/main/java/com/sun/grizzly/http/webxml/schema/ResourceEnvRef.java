package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class ResourceEnvRef {
	public List<String> description;  
    public String resourceEnvRefName;
    public String resourceEnvRefType;
    public String mappedName;
    public List<InjectionTarget> injectionTarget;
    
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public String getResourceEnvRefName() {
		return resourceEnvRefName;
	}
	public void setResourceEnvRefName(String resourceEnvRefName) {
		this.resourceEnvRefName = resourceEnvRefName;
	}
	public String getResourceEnvRefType() {
		return resourceEnvRefType;
	}
	public void setResourceEnvRefType(String resourceEnvRefType) {
		this.resourceEnvRefType = resourceEnvRefType;
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
		buffer.append("<ResourceEnvRef>").append("\n");
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
		buffer.append("<resourceEnvRefName>").append(resourceEnvRefName).append("</resourceEnvRefName>").append("\n");
		buffer.append("<resourceEnvRefType>").append(resourceEnvRefType).append("</resourceEnvRefType>").append("\n");
		buffer.append("</ResourceEnvRef>");
		return buffer.toString();
	}
}
