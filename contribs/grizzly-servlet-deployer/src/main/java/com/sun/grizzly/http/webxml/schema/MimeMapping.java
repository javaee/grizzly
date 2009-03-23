package com.sun.grizzly.http.webxml.schema;

public class MimeMapping {
	public String extension;
    public String mimeType;
    
	public String getExtension() {
		return extension;
	}
	public void setExtension(String extension) {
		this.extension = extension;
	}
	public String getMimeType() {
		return mimeType;
	}
	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}
	
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<MimeMapping>").append("\n");
		buffer.append("<extension>").append(extension).append("</extension>").append("\n");
		buffer.append("<mimeType>").append(mimeType).append("</mimeType>").append("\n");
		buffer.append("</MimeMapping>");
		return buffer.toString();
	}
}
