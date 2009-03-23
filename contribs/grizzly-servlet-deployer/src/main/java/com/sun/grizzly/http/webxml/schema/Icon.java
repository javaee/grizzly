package com.sun.grizzly.http.webxml.schema;

public class Icon {
	public String smallIcon;
	public String largeIcon;
	
	public String getSmallIcon() {
		return smallIcon;
	}
	public void setSmallIcon(String smallIcon) {
		this.smallIcon = smallIcon;
	}
	public String getLargeIcon() {
		return largeIcon;
	}
	public void setLargeIcon(String largeIcon) {
		this.largeIcon = largeIcon;
	}


	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<icon>").append("\n");
		buffer.append("<largeIcon>").append(largeIcon).append("</largeIcon>").append("\n");;
		buffer.append("<smallIcon>").append(smallIcon).append("</smallIcon>").append("\n");;
		buffer.append("</icon>");
		return buffer.toString();
	}
	
}
