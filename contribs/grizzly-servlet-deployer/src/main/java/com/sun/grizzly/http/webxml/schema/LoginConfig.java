package com.sun.grizzly.http.webxml.schema;

public class LoginConfig {
	public String authMethod;
    public String realmName;
    public FormLoginConfig formLoginConfig;
    
	public String getAuthMethod() {
		return authMethod;
	}
	public void setAuthMethod(String authMethod) {
		this.authMethod = authMethod;
	}
	public String getRealmName() {
		return realmName;
	}
	public void setRealmName(String realmName) {
		this.realmName = realmName;
	}
	public FormLoginConfig getFormLoginConfig() {
		return formLoginConfig;
	}
	public void setFormLoginConfig(FormLoginConfig formLoginConfig) {
		this.formLoginConfig = formLoginConfig;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<LoginConfig>").append("\n");
		buffer.append("<authMethod>").append(authMethod).append("</authMethod>").append("\n");
		buffer.append("<realmName>").append(realmName).append("</realmName>").append("\n");
		
		if(formLoginConfig!=null){
			buffer.append(formLoginConfig).append("\n");
		}
		
		buffer.append("</LoginConfig>");
		return buffer.toString();
	}
}
