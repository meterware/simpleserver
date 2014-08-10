package com.meterware.simpleserver;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * A listener for messages received by the server.
 */
public interface ServerListener {

    /**
     * Invoked when the server accepts a connection.
     */
    void connectionAccepted(SocketChannel channel);

    /**
     * Invoked when the server receives a buffer of data.
     *
     * @param responder an object which can be used to issue a response and/or act as a key for context.
     */
    void dataRead(Responder responder) throws IOException;

    /**
     * Invoked after the server sends data.
     *
     * @param numBytes the number of bytes sent.
     */
    void dataSent(int numBytes);
}
