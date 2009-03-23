package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class UserDataConstraint {
	public List<String> description;  
    public String transportGuarantee;

    public List<String> getDescription() {
		return description;
	}
	public void setDescription(List<String> description) {
		this.description = description;
	}
	public String getTransportGuarantee() {
		return transportGuarantee;
	}
	public void setTransportGuarantee(String transportGuarantee) {
		this.transportGuarantee = transportGuarantee;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<UserDataConstraint>").append("\n");
		if(description!=null && description.size()>0){
			List<String> list = description;
			
			for (String item : list) {
				buffer.append("<description>").append(item).append("</description>").append("\n");
			}
		} 
		buffer.append("<transportGuarantee>").append(transportGuarantee).append("</transportGuarantee>").append("\n");
		buffer.append("</UserDataConstraint>");
		return buffer.toString();
	}
}
