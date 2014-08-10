package com.meterware.simpleserver;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * An interface which allows a server to respond to requests.
 */
public interface Responder {

    /**
     * Returns the just-received request as an array of bytes
     *
     * @return a byte array containing the request
     */
    byte[] getDataAsBytes() throws IOException;

    /**
     * Returns the just-received request in a byte buffer.
     */
    ByteBuffer getData() throws IOException;

    /**
     * Sends a response as an array of bytes and then closes the connection.
     *
     * @param bytes the contents of the response
     */
    void sendReplyAndClose(byte[] bytes) throws IOException;

    /**
     * Sends a response as an array of bytes.
     *
     * @param bytes the contents of the response
     */
    void sendReply(byte[] bytes) throws IOException;

    /**
     * Sends a response from a byte buffer and then closes the connection. Note that the buffer position
     * must indicate the first byte to be sent, with the limit indicating the byte after the last to be sent.
     *
     * @param byteBuffer the outgoing response
     */
    void sendReplyAndClose(ByteBuffer byteBuffer) throws IOException;

    /**
     * Sends a response from a byte buffer. Note that the buffer position must indicate the first
     * byte to be sent, with the limit indicating the byte after the last to be sent.
     *
     * @param byteBuffer the outgoing response
     */
    void sendReply(ByteBuffer byteBuffer) throws IOException;

    /**
     * Closes the connection
     */
    void closeConnectionImmediately() throws IOException;
}
