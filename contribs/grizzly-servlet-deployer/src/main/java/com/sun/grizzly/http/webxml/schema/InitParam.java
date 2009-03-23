package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class InitParam {
	public String paramName;
    public String paramValue;
    public List<String> description;  
    
	public String getParamName() {
		return paramName;
	}
	public void setParamName(String paramName) {
		this.paramName = paramName;
	}
	public String getParamValue() {
		return paramValue;
	}
	public void setParamValue(String paramValue) {
		this.paramValue = paramValue;
	}
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<InitParam>").append("\n");
		buffer.append("<paramName>").append(paramName).append("</paramName>").append("\n");
		buffer.append("<paramValue>").append(paramValue).append("</paramValue>").append("\n");
		
		if(description!=null && description.size()>0){
			List<String> list = description;
			
			for (String item : list) {
				buffer.append("<description>").append(item).append("</description>").append("\n");
			}
		} 
		
		buffer.append("</InitParam>");
		return buffer.toString();
	}
    
}
