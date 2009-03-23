package com.sun.grizzly.http.webxml.schema;

public class PortComponentRef {
	public String serviceEndpointInterface;
    public boolean enableMtom;
    public String portComponentLink;
    
	public String getServiceEndpointInterface() {
		return serviceEndpointInterface;
	}
	public void setServiceEndpointInterface(String serviceEndpointInterface) {
		this.serviceEndpointInterface = serviceEndpointInterface;
	}
	public boolean getEnableMtom() {
		return enableMtom;
	}
	public void setEnableMtom(boolean enableMtom) {
		this.enableMtom = enableMtom;
	}
	public String getPortComponentLink() {
		return portComponentLink;
	}
	public void setPortComponentLink(String portComponentLink) {
		this.portComponentLink = portComponentLink;
	}
	
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<PortComponentRef>").append("\n");
		buffer.append("<enableMtom>").append(enableMtom).append("</enableMtom>").append("\n");
		buffer.append("<portComponentLink>").append(portComponentLink).append("</portComponentLink>").append("\n");
		buffer.append("<serviceEndpointInterface>").append(serviceEndpointInterface).append("</serviceEndpointInterface>").append("\n");
		buffer.append("</PortComponentRef>");
		return buffer.toString();
	}

    
}
