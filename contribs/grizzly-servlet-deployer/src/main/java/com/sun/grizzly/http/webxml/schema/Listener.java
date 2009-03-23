package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class Listener {
	public String listenerClass;
	public List<Icon> icon;
	public List<String> displayName;  
	public List<String> description;  
	
	public String getListenerClass() {
		return listenerClass;
	}

	public void setListenerClass(String listenerClass) {
		this.listenerClass = listenerClass;
	}

	public List<Icon> getIcon() {
		return icon;
	}

	public void setIcon(List<Icon> icon) {
		this.icon = icon;
	}

	public List<String> getDisplayName() {
		return displayName;
	}

	public void setDisplayName(List<String> displayName) {
		this.displayName = displayName;
	}

	public List<String> getDescription() {
		return description;
	}

	public void setDescription(List<String> description) {
		this.description = description;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<Listener>").append("\n");
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
		buffer.append("<listenerClass>").append(listenerClass).append("</listenerClass>").append("\n");
		if(icon!=null && icon.size()>0){
			List<Icon> list = icon;
			
			for (Icon item : list) {
				buffer.append(item).append("\n");
			}
		} 
		buffer.append("</Listener>");
		return buffer.toString();
	}
}
