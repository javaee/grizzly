package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class EnvEntry {
	public List<String> description;  
    public String envEntryName;
    public String envEntryValue;
    public String envEntryType;
    public String mappedName;
    public List<InjectionTarget> injectionTarget;
    
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public String getEnvEntryName() {
		return envEntryName;
	}
	public void setEnvEntryName(String envEntryName) {
		this.envEntryName = envEntryName;
	}
	public String getEnvEntryValue() {
		return envEntryValue;
	}
	public void setEnvEntryValue(String envEntryValue) {
		this.envEntryValue = envEntryValue;
	}
	public String getEnvEntryType() {
		return envEntryType;
	}
	public void setEnvEntryType(String envEntryType) {
		this.envEntryType = envEntryType;
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
		buffer.append("<EnvEntry>").append("\n");
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
		buffer.append("<envEntryName>").append(envEntryName).append("</envEntryName>").append("\n");
		buffer.append("<envEntryType>").append(envEntryType).append("</envEntryType>").append("\n");
		buffer.append("<envEntryValue>").append(envEntryValue).append("</envEntryValue>").append("\n");
		buffer.append("</EnvEntry>");
		return buffer.toString();
	}
    
}
