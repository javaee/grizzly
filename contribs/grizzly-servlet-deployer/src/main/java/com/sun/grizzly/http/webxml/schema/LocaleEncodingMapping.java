package com.sun.grizzly.http.webxml.schema;

public class LocaleEncodingMapping {
	public String locale;
	public String encoding;
	
	public String getLocale() {
		return locale;
	}
	public void setLocale(String locale) {
		this.locale = locale;
	}
	public String getEncoding() {
		return encoding;
	}
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}
	
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<LocaleEncodingMapping>").append("\n");
		buffer.append("<encoding>").append(encoding).append("</encoding>").append("\n");
		buffer.append("<locale>").append(locale).append("</locale>").append("\n");
		buffer.append("</LocaleEncodingMapping>");
		return buffer.toString();
	}
	
}
