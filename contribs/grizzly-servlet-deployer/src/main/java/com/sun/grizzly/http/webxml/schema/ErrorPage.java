package com.sun.grizzly.http.webxml.schema;

public class ErrorPage {
	public String errorCode; 
	public String exceptionType;
    public String location;
    
	public String getErrorCode() {
		return errorCode;
	}
	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}
	public String getExceptionType() {
		return exceptionType;
	}
	public void setExceptionType(String exceptionType) {
		this.exceptionType = exceptionType;
	}
	public String getLocation() {
		return location;
	}
	public void setLocation(String location) {
		this.location = location;
	}
		
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<ErrorPage>").append("\n");
		buffer.append("<errorCode>").append(errorCode).append("</errorCode>").append("\n");
		buffer.append("<exceptionType>").append(exceptionType).append("</exceptionType>").append("\n");
		buffer.append("<location>").append(location).append("</location>").append("\n");
		buffer.append("</ErrorPage>");
		return buffer.toString();
	}
    
}
