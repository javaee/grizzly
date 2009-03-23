package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class MessageDestination {
	public List<String> description;
    public List<String> displayName;  
    public List<Icon> icon;
    public String messageDestinationName;
    public String mappedName;
    
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public List<String> getDisplayName() {
		return displayName;
	}
	public void setDisplayName(List<String> displayName) {
		this.displayName = displayName;
	}
	public List<Icon> getIcon() {
		return icon;
	}
	public void setIcon(List<Icon> icon) {
		this.icon = icon;
	}
	public String getMessageDestinationName() {
		return messageDestinationName;
	}
	public void setMessageDestinationName(String messageDestinationName) {
		this.messageDestinationName = messageDestinationName;
	}
	public String getMappedName() {
		return mappedName;
	}
	public void setMappedName(String mappedName) {
		this.mappedName = mappedName;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<MessageDestination>").append("\n");
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
		buffer.append("<mappedName>").append(mappedName).append("</mappedName>").append("\n");
		buffer.append("<messageDestinationName>").append(messageDestinationName).append("</messageDestinationName>").append("\n");
		buffer.append("</MessageDestination>");
		return buffer.toString();
	}
    
}
