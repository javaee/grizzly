package com.sun.grizzly.http.webxml.schema;

import java.util.List;

import javax.xml.namespace.QName;

public class ServiceRefHandler {
	public List<Icon> icon;
    public List<String> displayName;  
    public List<String> description;
    public String handlerName;
    public String handlerClass;
    public List<InitParam> initParam;
    public List<QName> soapHeader;
    public List<String> soapRole;
    public List<String> portName;
    
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
	public String getHandlerName() {
		return handlerName;
	}
	public void setHandlerName(String handlerName) {
		this.handlerName = handlerName;
	}
	public String getHandlerClass() {
		return handlerClass;
	}
	public void setHandlerClass(String handlerClass) {
		this.handlerClass = handlerClass;
	}
	public List<InitParam> getInitParam() {
		return initParam;
	}
	public void setInitParam(List<InitParam> initParam) {
		this.initParam = initParam;
	}
	public List<QName> getSoapHeader() {
		return soapHeader;
	}
	public void setSoapHeader(List<QName> soapHeader) {
		this.soapHeader = soapHeader;
	}
	public List<String> getSoapRole() {
		return soapRole;
	}
	public void setSoapRole(List<String> soapRole) {
		this.soapRole = soapRole;
	}
	public List<String> getPortName() {
		return portName;
	}
	public void setPortName(List<String> portName) {
		this.portName = portName;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<ServiceRefHandler>").append("\n");
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
		buffer.append("<handlerClass>").append(handlerClass).append("</handlerClass>").append("\n");
		buffer.append("<handlerName>").append(handlerName).append("</handlerName>").append("\n");
		if(icon!=null && icon.size()>0){
			List<Icon> list = icon;
			
			for (Icon item : list) {
				buffer.append(item).append("\n");
			}
		} 
		buffer.append("<initParam>").append(initParam).append("</initParam>").append("\n");
		buffer.append("<portName>").append(portName).append("</portName>").append("\n");
		
		if(soapHeader!=null && soapHeader.size()>0){
			List<QName> list = soapHeader;
			
			for (QName item : list) {
				buffer.append(item).append("\n");
			}
		} 
		
		buffer.append("<soapRole>").append(soapRole).append("</soapRole>").append("\n");
		buffer.append("</ServiceRefHandler>");
		return buffer.toString();
	}
    
    
}
