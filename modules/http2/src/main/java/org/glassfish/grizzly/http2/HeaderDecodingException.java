package org.glassfish.grizzly.http2;

import org.glassfish.grizzly.http2.frames.ErrorCode;

public class HeaderDecodingException extends RuntimeException {

    private final ErrorCode errorCode;
    private final ErrorType errorType;

    enum ErrorType {
        STREAM,
        SESSION
    }

    /**
     * Construct <tt>HeaderDecodingException</tt>.
     *
     * @param errorCode the {@link ErrorCode} for this {@link HeaderDecodingException}
     */
    public HeaderDecodingException(final ErrorCode errorCode, final ErrorType errorType) {
        this(errorCode, errorType, null);
    }

    /**
     * Construct <tt>HeaderDecodingException</tt>.
     *
     * @param errorCode the {@link ErrorCode} for this {@link HeaderDecodingException}
     * @param message the detail message for this {@link HeaderDecodingException}
     */
    public HeaderDecodingException(final ErrorCode errorCode,
                                   final ErrorType errorType,
                                   final String message) {
        super(message);
        this.errorCode = errorCode;
        this.errorType = errorType;
    }

    /**
     * @return the {@link ErrorCode} reason to be sent with a GOAWAY or RST frame.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * @return the {@link ErrorType} to control the type of frame to be sent to the peer.
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(128);
        sb.append(getClass().getName())
                .append(" errorCode=").append(errorCode).append(" errorType=").append(errorType);

        String message = getLocalizedMessage();

        return message != null ?
                (sb.append(": ").append(message).toString()) : sb.toString();
    }
    
}
