package com.sun.grizzly.http.webxml.schema;

import java.util.List;


public class FilterMapping {
	public String filterName; 
	public List<String> urlPattern;
    public List<String> servletName;
    public List<String> dispatcher;
    
	public String getFilterName() {
		return filterName;
	}
	public void setFilterName(String filterName) {
		this.filterName = filterName;
	}
	public List<String> getUrlPattern() {
		return urlPattern;
	}
	public void setUrlPattern(List<String> urlPattern) {
		this.urlPattern = urlPattern;
	}
	public List<String> getServletName() {
		return servletName;
	}
	public void setServletName(List<String> servletName) {
		this.servletName = servletName;
	}
	public List<String> getDispatcher() {
		return dispatcher;
	}
	public void setDispatcher(List<String> dispatcher) {
		this.dispatcher = dispatcher;
	}
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<FilterMapping>").append("\n");
		buffer.append("<filterName>").append(filterName).append("</filterName>").append("\n");
		
		
		if(servletName!=null && servletName.size()>0){
			List<String> list = servletName;
			
			for (String item : list) {
				buffer.append("<servletName>").append(item).append("</servletName>").append("\n");
			}
		} 
		
		if(urlPattern!=null && urlPattern.size()>0){
			List<String> list = urlPattern;
			
			for (String item : list) {
				buffer.append("<urlPattern>").append(item).append("</urlPattern>").append("\n");
			}
		} 
		
		if(dispatcher!=null && dispatcher.size()>0){
			for (String item : dispatcher) {
				buffer.append("<dispatcher>").append(item).append("</dispatcher>").append("\n");
			}
		}
		buffer.append("</FilterMapping>");
		return buffer.toString();
	}
	
}
