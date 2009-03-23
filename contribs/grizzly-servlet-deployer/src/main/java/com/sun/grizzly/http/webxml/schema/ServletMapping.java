package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class ServletMapping {
	public String servletName;
	public List<String> urlPattern;
	
	public String getServletName() {
		return servletName;
	}
	public void setServletName(String servletName) {
		this.servletName = servletName;
	}
	public List<String> getUrlPattern() {
		return urlPattern;
	}
	public void setUrlPattern(List<String> urlPattern) {
		this.urlPattern = urlPattern;
	}
		
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<ServletMapping>").append("\n");
		buffer.append("<servletName>").append(servletName).append("</servletName>").append("\n");
		
		if(urlPattern!=null && urlPattern.size()>0){
			List<String> list = urlPattern;
			
			for (String item : list) {
				buffer.append("<urlPattern>").append(item).append("</urlPattern>").append("\n");
			}
		} 
		
		buffer.append("</ServletMapping>");
		return buffer.toString();
	}
}
