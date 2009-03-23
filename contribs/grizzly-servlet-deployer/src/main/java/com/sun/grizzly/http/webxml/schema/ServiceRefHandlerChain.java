package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class ServiceRefHandlerChain {
	public String serviceNamePattern;
    public String portNamePattern;
    public List<String> protocolBindings;
    public List<ServiceRefHandler> handler;
    
	public String getServiceNamePattern() {
		return serviceNamePattern;
	}
	public void setServiceNamePattern(String serviceNamePattern) {
		this.serviceNamePattern = serviceNamePattern;
	}
	public String getPortNamePattern() {
		return portNamePattern;
	}
	public void setPortNamePattern(String portNamePattern) {
		this.portNamePattern = portNamePattern;
	}
	public List<String> getProtocolBindings() {
		return protocolBindings;
	}
	public void setProtocolBindings(List<String> protocolBindings) {
		this.protocolBindings = protocolBindings;
	}
	public List<ServiceRefHandler> getHandler() {
		return handler;
	}
	public void setHandler(List<ServiceRefHandler> handler) {
		this.handler = handler;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<ServiceRefHandlerChain>").append("\n");
		buffer.append("<portNamePattern>").append(portNamePattern).append("</portNamePattern>").append("\n");
		
		if(handler!=null && handler.size()>0){
			List<ServiceRefHandler> list = handler;
			
			for (ServiceRefHandler item : list) {
				buffer.append(item).append("\n");
			}
		} 
		
		if(protocolBindings!=null && protocolBindings.size()>0){
			buffer.append("<protocolBindings>").append("\n");
			
			List<String> list = protocolBindings;
			
			for (String item : list) {
				buffer.append(item).append("\n");
			}
			buffer.append("</protocolBindings>").append("\n");
		} 
		
		buffer.append("<serviceNamePattern>").append(serviceNamePattern).append("</serviceNamePattern>").append("\n");
		buffer.append("</ServiceRefHandlerChain>");
		return buffer.toString();
	}
    
}
