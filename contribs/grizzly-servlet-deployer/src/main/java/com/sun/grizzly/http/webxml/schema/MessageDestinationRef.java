package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class MessageDestinationRef {
	public List<String> description;  
    public String messageDestinationRefName;
    public String messageDestinationType;
    public String messageDestinationUsage;
    public String messageDestinationLink;
    public String mappedName;
    public List<InjectionTarget> injectionTarget;
    
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public String getMessageDestinationRefName() {
		return messageDestinationRefName;
	}
	public void setMessageDestinationRefName(String messageDestinationRefName) {
		this.messageDestinationRefName = messageDestinationRefName;
	}
	public String getMessageDestinationType() {
		return messageDestinationType;
	}
	public void setMessageDestinationType(String messageDestinationType) {
		this.messageDestinationType = messageDestinationType;
	}
	public String getMessageDestinationUsage() {
		return messageDestinationUsage;
	}
	public void setMessageDestinationUsage(String messageDestinationUsage) {
		this.messageDestinationUsage = messageDestinationUsage;
	}
	public String getMessageDestinationLink() {
		return messageDestinationLink;
	}
	public void setMessageDestinationLink(String messageDestinationLink) {
		this.messageDestinationLink = messageDestinationLink;
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
		buffer.append("<MessageDestinationRef>").append("\n");
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
		buffer.append("<messageDestinationLink>").append(messageDestinationLink).append("</messageDestinationLink>").append("\n");
		buffer.append("<messageDestinationRefName>").append(messageDestinationRefName).append("</messageDestinationRefName>").append("\n");
		buffer.append("<messageDestinationType>").append(messageDestinationType).append("</messageDestinationType>").append("\n");
		buffer.append("<messageDestinationUsage>").append(messageDestinationUsage).append("</messageDestinationUsage>").append("\n");
		buffer.append("</MessageDestinationRef>");
		return buffer.toString();
	}
    
}
