package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class SecurityRoleRef {

	public List<String> description;  
    public String roleName;
    public String roleLink;
    
	public List<String> getDescription() {
		return description;
	}

	public void setDescription(List<String> description) {
		this.description = description;
	}

	public String getRoleName() {
		return roleName;
	}

	public void setRoleName(String roleName) {
		this.roleName = roleName;
	}

	public String getRoleLink() {
		return roleLink;
	}

	public void setRoleLink(String roleLink) {
		this.roleLink = roleLink;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<SecurityRoleRef>").append("\n");
		if(description!=null && description.size()>0){
			List<String> list = description;
			
			for (String item : list) {
				buffer.append("<description>").append(item).append("</description>").append("\n");
			}
		} 
		buffer.append("<roleName>").append(roleName).append("</roleName>").append("\n");
		buffer.append("<roleLink>").append(roleLink).append("</roleLink>").append("\n");
		buffer.append("</SecurityRoleRef>");
		return buffer.toString();
	}

}

