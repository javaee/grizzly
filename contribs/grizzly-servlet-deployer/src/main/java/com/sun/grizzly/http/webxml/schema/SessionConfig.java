package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class SessionConfig {
	public String sessionTimeout;
	public CookieConfig cookieConfig;
	public List<String> trackingMode;
	
	public SessionConfig(){
		
	}
	
	public SessionConfig(String sessionTimeout){
		this.sessionTimeout = sessionTimeout;
	}
	
	public String getSessionTimeout() {
		return sessionTimeout;
	}

	public void setSessionTimeout(String sessionTimeout) {
		this.sessionTimeout = sessionTimeout;
	}

	public CookieConfig getCookieConfig() {
		return cookieConfig;
	}

	public void setCookieConfig(CookieConfig cookieConfig) {
		this.cookieConfig = cookieConfig;
	}

	public List<String> getTrackingMode() {
		return trackingMode;
	}

	public void setTrackingMode(List<String> trackingMode) {
		this.trackingMode = trackingMode;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<SessionConfig>").append("\n");
		buffer.append("<sessionTimeout>").append(sessionTimeout).append("</sessionTimeout>").append("\n");
		buffer.append("<cookieConfig>").append(cookieConfig).append("</cookieConfig>").append("\n");
		
		if(trackingMode!=null && trackingMode.size()>0){
			for (String item : trackingMode) {
				buffer.append("<trackingMode>").append(item).append("</trackingMode>").append("\n");
			}
		}
		
		buffer.append("</SessionConfig>");
		return buffer.toString();
	}
	
	
}
