package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class PersistenceUnitRef {
	public List<String> description;  
    public String persistenceUnitRefName;
    public String persistenceUnitName;
    public String mappedName;
    public List<InjectionTarget> injectionTarget;
    
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public String getPersistenceUnitRefName() {
		return persistenceUnitRefName;
	}
	public void setPersistenceUnitRefName(String persistenceUnitRefName) {
		this.persistenceUnitRefName = persistenceUnitRefName;
	}
	public String getPersistenceUnitName() {
		return persistenceUnitName;
	}
	public void setPersistenceUnitName(String persistenceUnitName) {
		this.persistenceUnitName = persistenceUnitName;
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
		buffer.append("<PersistenceUnitRef>").append("\n");
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
		buffer.append("<persistenceUnitName>").append(persistenceUnitName).append("</persistenceUnitName>").append("\n");
		buffer.append("<persistenceUnitRefName>").append(persistenceUnitRefName).append("</persistenceUnitRefName>").append("\n");
		buffer.append("</PersistenceUnitRef>");
		return buffer.toString();
	}
    	
}
