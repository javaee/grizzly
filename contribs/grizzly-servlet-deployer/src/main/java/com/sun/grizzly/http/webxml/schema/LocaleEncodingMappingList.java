package com.sun.grizzly.http.webxml.schema;

import java.util.List;

public class LocaleEncodingMappingList {
	public List<LocaleEncodingMapping> localeEncodingMapping;

	public List<LocaleEncodingMapping> getLocaleEncodingMapping() {
		return localeEncodingMapping;
	}

	public void setLocaleEncodingMapping(List<LocaleEncodingMapping> localeEncodingMapping) {
		this.localeEncodingMapping = localeEncodingMapping;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<LocaleEncodingMappingList>").append("\n");
		
		if(localeEncodingMapping!=null && localeEncodingMapping.size()>0){
			List<LocaleEncodingMapping> list = localeEncodingMapping;
			
			for (LocaleEncodingMapping item : list) {
				buffer.append(item).append("\n");
			}
		} 
		
		buffer.append("</LocaleEncodingMappingList>");
		return buffer.toString();
	}
	
}
