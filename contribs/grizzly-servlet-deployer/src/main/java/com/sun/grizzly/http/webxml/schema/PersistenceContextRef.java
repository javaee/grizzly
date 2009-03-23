package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class PersistenceContextRef {
	public List<String> description;  
    public String persistenceContextRefName;
    public String persistenceUnitName;
    public String persistenceContextType;
    public List<Property> persistenceProperty;
    public String mappedName;
    public List<InjectionTarget> injectionTarget;
    
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public String getPersistenceContextRefName() {
		return persistenceContextRefName;
	}
	public void setPersistenceContextRefName(String persistenceContextRefName) {
		this.persistenceContextRefName = persistenceContextRefName;
	}
	public String getPersistenceUnitName() {
		return persistenceUnitName;
	}
	public void setPersistenceUnitName(String persistenceUnitName) {
		this.persistenceUnitName = persistenceUnitName;
	}
	public String getPersistenceContextType() {
		return persistenceContextType;
	}
	public void setPersistenceContextType(String persistenceContextType) {
		this.persistenceContextType = persistenceContextType;
	}
	public List<Property> getPersistenceProperty() {
		return persistenceProperty;
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
	public void setPersistenceProperty(List<Property> persistenceProperty) {
		this.persistenceProperty = persistenceProperty;
	}
	/**
		 * 
		 * @return 
		 * @author 
		 */
		public String toString() {
			StringBuffer buffer = new StringBuffer();
			buffer.append("<PersistenceContextRef>").append("\n");
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
			buffer.append("<persistenceContextRefName>").append(persistenceContextRefName).append("</persistenceContextRefName>").append("\n");
			buffer.append("<persistenceContextType>").append(persistenceContextType).append("</persistenceContextType>").append("\n");
			
			if(persistenceProperty!=null && persistenceProperty.size()>0){
				List<Property> list = persistenceProperty;
				
				for (Property item : list) {
					buffer.append(item).append("\n");
				}
			} 
			
			buffer.append("<persistenceUnitName>").append(persistenceUnitName).append("</persistenceUnitName>").append("\n");
			buffer.append("</PersistenceContextRef>");
			return buffer.toString();
		}
    
}
