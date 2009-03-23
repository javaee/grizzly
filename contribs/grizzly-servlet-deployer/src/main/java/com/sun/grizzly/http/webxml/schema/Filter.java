package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class Filter {
	
	public List<Icon> icon;
    public String filterName;
    public List<String> displayName;  
    public List<String> description;  
    public String filterClass;
    public boolean asyncSupported;
    public String asyncTimeout;
    public List<InitParam> initParam;
    
	public List<Icon> getIcon() {
		return icon;
	}
	public void setIcon(List<Icon> icon) {
		this.icon = icon;
	}
	public String getFilterName() {
		return filterName;
	}
	public void setFilterName(String filterName) {
		this.filterName = filterName;
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
	public String getFilterClass() {
		return filterClass;
	}
	public void setFilterClass(String filterClass) {
		this.filterClass = filterClass;
	}
	public List<InitParam> getInitParam() {
		return initParam;
	}
	public void setInitParam(List<InitParam> initParam) {
		this.initParam = initParam;
	}
	public boolean getAsyncSupported() {
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
		buffer.append("<Filter>").append("\n");
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
		buffer.append("<filterClass>").append(filterClass).append("</filterClass>").append("\n");
		buffer.append("<filterName>").append(filterName).append("</filterName>").append("\n");
		buffer.append("<asyncSupported>").append(asyncSupported).append("</asyncSupported>").append("\n");
		buffer.append("<asyncTimeout>").append(asyncTimeout).append("</asyncTimeout>").append("\n");
		
		if(icon!=null && icon.size()>0){
			List<Icon> list = icon;
			
			for (Icon item : list) {
				buffer.append(item).append("\n");
			}
		} 
		
		if(initParam!=null){
			for (InitParam param : initParam) {
				buffer.append(param).append("\n");
			}
		}
		
		buffer.append("</Filter>");
		return buffer.toString();
		
	}
    
    
}
