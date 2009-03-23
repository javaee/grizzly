package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class AuthConstraint {
	public List<String> description;  
    public List<String> roleName;
    
	public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public List<String> getRoleName() {
		return roleName;
	}
	public void setRoleName(List<String> roleName) {
		this.roleName = roleName;
	}
    
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<AuthConstraint>").append("\n");
		
		if(description!=null && description.size()>0){
			List<String> list = description;
			
			for (String item : list) {
				buffer.append("<description>").append(item).append("</description>").append("\n");
			}
		}  
		
		if(roleName!=null && roleName.size()>0){
			for (String role : roleName) {
				buffer.append("<roleName>").append(role).append("</roleName>").append("\n");
			}
		}
		
		buffer.append("</AuthConstraint>");
		return buffer.toString();
	}
}
