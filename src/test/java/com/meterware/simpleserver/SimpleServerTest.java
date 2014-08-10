package com.meterware.simpleserver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import static java.lang.Thread.sleep;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.number.OrderingComparison.greaterThan;

/**
 * Verifies the basic behavior of the simple server
 */
public class SimpleServerTest implements ServerListener {

    private static SimpleServer server;
    private final byte[] BYTE_MESSAGE = {1, 8, 2, 7, 23};
    private final byte[] BYTE_REPLY = {9, 8, 7, 6, 5};
    private SocketChannel connectedChannel;
    private Responder responder;
    private final Object receiveSemaphore = new Object();
    private final Object sendSemaphore = new Object();

    @Before
    public void setUp() throws IOException {
        server = SimpleServer.create(this);
    }

    @After
    public void tearDown() throws Exception {
        server.shutDown();
    }

    @Override
    public void connectionAccepted(SocketChannel channel) {
        connectedChannel = channel;
    }

    @Override
    public void dataRead(Responder responder) throws IOException {
        setResponder(responder);
    }

    private void setResponder(Responder responder) {
        this.responder = responder;
        synchronized (receiveSemaphore) {
            receiveSemaphore.notify();
        }
    }

    @Override
    public void dataSent(int numBytes) {
        synchronized (sendSemaphore) {
            sendSemaphore.notify();
        }
    }

    @Test
    public void whenServerCreated_isNotNull() {
        assertThat(server, instanceOf(SimpleServer.class));
    }

    @Test
    public void whenServerCreated_hasSelectedPort() {
        assertThat(server.getPort(), greaterThan(0));
    }

    @Test(timeout = 1000)
    public void afterServerCreated_acceptsConnections() throws IOException, InterruptedException {
        new Socket("localhost", server.getPort());
        sleep(5);
        assertThat(connectedChannel, notNullValue());
    }

    @Test(timeout = 1000)
    public void afterConnectionEstablished_sentBytesTriggersCallback() throws IOException, InterruptedException {
        Socket socket = new Socket("localhost", server.getPort());

        socket.getOutputStream().write(BYTE_MESSAGE);
        waitForRequestReceived();

        assertThat(responder, notNullValue());
    }

    private void waitForRequestReceived() throws InterruptedException {
        synchronized (receiveSemaphore) {
            receiveSemaphore.wait(1000);
        }
    }

    private void waitForReplySent() throws InterruptedException {
        synchronized (sendSemaphore) {
            sendSemaphore.wait(1000);
        }
    }

    @Test(timeout = 1000)
    public void afterCallback_readRequestAsBytes() throws IOException, InterruptedException {
        Socket socket = new Socket("localhost", server.getPort());

        socket.getOutputStream().write(BYTE_MESSAGE);
        waitForRequestReceived();

        assertThat(responder.getDataAsBytes(), is(BYTE_MESSAGE));
    }

    @Test(timeout = 1000)
    public void afterCallback_readRequestAsByteBuffer() throws IOException, InterruptedException {
        Socket socket = new Socket("localhost", server.getPort());

        socket.getOutputStream().write(BYTE_MESSAGE);
        waitForRequestReceived();

        ByteBuffer buffer = responder.getData();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        assertThat(data, is(BYTE_MESSAGE));
    }

    @Test(timeout = 1000)
    public void afterResponseSent_readFromSocket() throws IOException, InterruptedException {
        Socket socket = new Socket("localhost", server.getPort());
        socket.getOutputStream().write(BYTE_MESSAGE);
        waitForRequestReceived();
        responder.sendReply(BYTE_REPLY);

        waitForReplySent();
        int available = socket.getInputStream().available();

        byte[] buffer = new byte[available];
        int bytesRead = socket.getInputStream().read(buffer);
        assertThat(bytesRead, is(BYTE_REPLY.length));
        assertThat(buffer, is(BYTE_REPLY));
    }

}

/*
    when server running, accepts connections
    when connection accepted, listen for read on channel
    when message received, invoke callback
    within callback, read contents as byte array
    within callback, read contents as bytebuffer
    when response written, listen for write on channel
    when response complete, stop listening for writes
 */
