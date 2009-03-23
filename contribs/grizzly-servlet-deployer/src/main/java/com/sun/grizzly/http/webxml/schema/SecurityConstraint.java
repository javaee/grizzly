package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class SecurityConstraint {
    public List<String> displayName;  
    public List<WebResourceCollection> webResourceCollection;
    public AuthConstraint authConstraint;
    public UserDataConstraint userDataConstraint;
    
	public List<String> getDisplayName() {
		return displayName;
	}
	public void setDisplayName(List<String> displayName) {
		this.displayName = displayName;
	}
	public List<WebResourceCollection> getWebResourceCollection() {
		return webResourceCollection;
	}
	public void setWebResourceCollection(List<WebResourceCollection> webResourceCollection) {
		this.webResourceCollection = webResourceCollection;
	}
	public AuthConstraint getAuthConstraint() {
		return authConstraint;
	}
	public void setAuthConstraint(AuthConstraint authConstraint) {
		this.authConstraint = authConstraint;
	}
	public UserDataConstraint getUserDataConstraint() {
		return userDataConstraint;
	}
	public void setUserDataConstraint(UserDataConstraint userDataConstraint) {
		this.userDataConstraint = userDataConstraint;
	}
	
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<SecurityConstraint>").append("\n");
		if(displayName!=null && displayName.size()>0){
			List<String> list = displayName;
			
			for (String item : list) {
				buffer.append("<displayName>").append(item).append("</displayName>").append("\n");
			}
		} 
		
		if(userDataConstraint!=null){
			buffer.append(userDataConstraint).append("\n");
		}
		
		if(authConstraint!=null){
			buffer.append(authConstraint).append("\n");
		}

		if(displayName!=null && displayName.size()>0){
			List<String> list = displayName;
			
			for (String item : list) {
				buffer.append("<displayName>").append(item).append("</displayName>").append("\n");
			}
		} 
		
		buffer.append("</SecurityConstraint>");
		return buffer.toString();
	}
}
