package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class WebResourceCollection {
	public String webResourceName;
    public List<String> description;  
    public List<String> urlPattern;
    public List<String> httpMethod;
    
	public String getWebResourceName() {
		return webResourceName;
	}
	public void setWebResourceName(String webResourceName) {
		this.webResourceName = webResourceName;
	}
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public List<String> getUrlPattern() {
		return urlPattern;
	}
	public void setUrlPattern(List<String> urlPattern) {
		this.urlPattern = urlPattern;
	}
	public List<String> getHttpMethod() {
		return httpMethod;
	}
	public void setHttpMethod(List<String> httpMethod) {
		this.httpMethod = httpMethod;
	}

	public String toString() {
		
		StringBuffer buffer = new StringBuffer();
		buffer.append("<WebResourceCollection>").append("\n");
		if(description!=null && description.size()>0){
			List<String> list = description;
			
			for (String item : list) {
				buffer.append("<description>").append(item).append("</description>").append("\n");
			}
		} 
		buffer.append("<webResourceName>").append(webResourceName).append("</webResourceName>").append("\n");
		
		if(httpMethod!=null && httpMethod.size()>0){
			for (String item : httpMethod) {
				buffer.append("<httpMethod>").append(item).append("</httpMethod>").append("\n");
			}
		}
		
		if(urlPattern!=null && urlPattern.size()>0){
			List<String> list = urlPattern;
			
			for (String item : list) {
				buffer.append("<urlPattern>").append(item).append("</urlPattern>").append("\n");
			}
		} 
		
		buffer.append("</WebResourceCollection>");
		return buffer.toString();
		
	}
		
}
