package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class ServiceRefHandlerChains {
	public List<ServiceRefHandlerChain> handlerChain;

	public List<ServiceRefHandlerChain> getHandlerChain() {
		return handlerChain;
	}

	public void setHandlerChain(List<ServiceRefHandlerChain> handlerChain) {
		this.handlerChain = handlerChain;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<ServiceRefHandlerChains>").append("\n");
		
		if(handlerChain!=null && handlerChain.size()>0){
			List<ServiceRefHandlerChain> list = handlerChain;
			
			for (ServiceRefHandlerChain item : list) {
				buffer.append(item).append("\n");
			}
		}
		
		buffer.append("</ServiceRefHandlerChains>");
		return buffer.toString();
	}
	
}
