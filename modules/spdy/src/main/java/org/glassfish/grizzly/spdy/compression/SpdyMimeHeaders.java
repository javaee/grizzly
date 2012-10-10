/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.grizzly.spdy.compression;

import java.util.Locale;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;

/**
 *
 * @author oleksiys
 */
public class SpdyMimeHeaders extends MimeHeaders {

    @Override
    public DataChunk addValue(final String name) {
        return super.addValue(name.toLowerCase(Locale.US));
    }

    @Override
    public DataChunk addValue(final Header header) {
        final byte[] lowerCaseBytes = header.getLowerCaseBytes();
        return super.addValue(lowerCaseBytes, 0, lowerCaseBytes.length);
    }

    @Override
    public DataChunk setValue(final String name) {
        return super.setValue(name.toLowerCase(Locale.US));
    }

    @Override
    public DataChunk setValue(final Header header) {
        final byte[] lowerCaseBytes = header.getLowerCaseBytes();
        
        for (int i = 0; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCaseLowerCase(lowerCaseBytes)) {
                for (int j = i + 1; j < count; j++) {
                    if (headers[j].getName().equalsIgnoreCaseLowerCase(lowerCaseBytes)) {
                        removeHeader(j--);
                    }
                }
                return headers[i].getValue();
            }
        }
        MimeHeaderField mh = createHeader();
        mh.getName().setBytes(lowerCaseBytes);
        
        return mh.getValue();
    }

    /**
     * Get the header's "serialized" flag.
     *
     * @param n the header index
     * @return the "serialized" flag value.
     */
    public boolean isSerialized(int n) {
        if (n >= 0 && n < count) {
            return headers[n].isSerialized();
        }
        
        return true;
    }
    
    /**
     * Get the header's "serialized" flag.
     *
     * @param n the header index
     * @param isSerialized  the "serialized" flag value.
     */
    public void setSerialized(final int n, final boolean isSerialized) {
        if (n >= 0 && n < count) {
            headers[n].setSerialized(isSerialized);
        }
    }
}
