package com.sun.grizzly.http.webxml.schema;

public class FormLoginConfig {
	public String formLoginPage;
    public String formErrorPage;
    
    public FormLoginConfig(){
    	
    }
    public FormLoginConfig(String formLoginPage, String formErrorPage){
    	this.formLoginPage = formLoginPage;
    	this.formErrorPage = formErrorPage;
    }
    
	public String getFormLoginPage() {
		return formLoginPage;
	}
	public void setFormLoginPage(String formLoginPage) {
		this.formLoginPage = formLoginPage;
	}
	public String getFormErrorPage() {
		return formErrorPage;
	}
	public void setFormErrorPage(String formErrorPage) {
		this.formErrorPage = formErrorPage;
	}
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<FormLoginConfig>").append("\n");
		buffer.append("<formErrorPage>").append(formErrorPage).append("</formErrorPage>").append("\n");
		buffer.append("<formLoginPage>").append(formLoginPage).append("</formLoginPage>").append("\n");
		buffer.append("</FormLoginConfig>");
		return buffer.toString();
	}
    
}
