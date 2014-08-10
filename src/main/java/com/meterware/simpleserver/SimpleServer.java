package com.meterware.simpleserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A simple NIO server, intended to simplify testing of socket-based clients.
 */
public class SimpleServer implements Runnable {
    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;
    private boolean active = true;
    private ServerListener listener;
    private Map<SocketChannel, ServerResponder> responders = new HashMap<>();
    private List<SelectionKey> pendingCloseRequests = new ArrayList<>();

    /**
     * Creates a server instance. The server will select a port on which to listen, which may be retrieved by
     * calling {@link #getPort()} Once the server is no longer needed, it should be disposed of via @{link #shutDown()}.
     *
     * @param listener a listener for server events that allow code to read and send messages.
     * @return a new server.
     * @throws IOException
     */
    public static SimpleServer create(ServerListener listener) throws IOException {
        return new SimpleServer(listener);
    }

    /**
     * Returns the number of the port to which client sockets should connect.
     * @return a port number
     */
    public int getPort() {
        try {
            return ((InetSocketAddress) serverSocketChannel.getLocalAddress()).getPort();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Shuts down this server.
     */
    public void shutDown() {
        active = false;
        selector.wakeup();
    }

    private SimpleServer(ServerListener listener) throws IOException {
        this.listener = listener;
        selector = Selector.open();
        serverSocketChannel = openServerSocketChannel();
        new Thread(this).start();
    }

    private ServerSocketChannel openServerSocketChannel() throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(null);
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        return serverSocketChannel;
    }

    @Override
    public void run() {
        while (active) {
            try {
                handleIOEvent();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void handleIOEvent() throws IOException {
        selector.select();

        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();
            handleSelection(key);
        }
    }

    private void handleSelection(SelectionKey key) throws IOException {
        if (key.isAcceptable()) acceptConnection(key);
        if (key.isReadable()) readRequest(key);
        if (key.isWritable()) writeResponse(key);
    }

    private void acceptConnection(SelectionKey key) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        if (channel == null) return;

        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        listener.connectionAccepted(channel);
    }

    private void readRequest(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        listener.dataRead(getResponder(channel, key));
    }

    private ServerResponder getResponder(SocketChannel channel, SelectionKey key) {
        if (responders.containsKey(channel))
            return responders.get(channel);
        else
            return createResponder(channel, key);
    }

    private ServerResponder createResponder(SocketChannel channel, SelectionKey key) {
        ServerResponder responder = new ServerResponder(channel, key);
        responders.put(channel, responder);
        return responder;
    }

    private void writeResponse(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer byteBuffer = getByteBuffer(key, channel);

        if (byteBuffer == null)
            channel.register(selector, SelectionKey.OP_READ);
        else
            sendResponse(channel, byteBuffer);

        if (pendingCloseRequests.contains(key))
            closePendingRequest(key);
    }

    private ByteBuffer getByteBuffer(SelectionKey key, SocketChannel channel) {
        ServerResponder responder = getResponder(channel, key);
        return responder.poll();
    }

    private void sendResponse(SocketChannel channel, ByteBuffer byteBuffer) throws IOException {
        int numBytes = byteBuffer.remaining();
        channel.write(byteBuffer);
        listener.dataSent(numBytes);
    }

    private void closePendingRequest(SelectionKey key) throws IOException {
        key.channel().close();
        key.cancel();
        pendingCloseRequests.remove(key);
    }

    class ServerResponder implements Responder {
        private static final int BUFFER_SIZE = 2048;
        private SelectionKey key;

        SocketChannel channel;
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        ConcurrentLinkedQueue<ByteBuffer> sentBuffers = new ConcurrentLinkedQueue<>();

        ServerResponder(SocketChannel channel, SelectionKey key) {
            this.channel = channel;
            this.key = key;
        }

        ByteBuffer poll() {
            return sentBuffers.poll();
        }

        @Override
        public byte[] getDataAsBytes() throws IOException {
            getData();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }

        @Override
        public ByteBuffer getData() throws IOException {
            buffer.clear();
            channel.read(buffer);
            buffer.flip();
            return buffer;
        }

        @Override
        public void sendReplyAndClose(byte[] bytes) throws IOException {
            sendReplyAndClose(ByteBuffer.wrap(bytes));
        }

        @Override
        public void sendReply(byte[] bytes) throws IOException {
            sendReply(ByteBuffer.wrap(bytes));
        }

        @Override
        public void sendReplyAndClose(ByteBuffer byteBuffer) throws IOException {
            sentBuffers.add(byteBuffer);
            channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            pendingCloseRequests.add(key);
        }

        @Override
        public void sendReply(ByteBuffer byteBuffer) throws IOException {
            sentBuffers.add(byteBuffer);
            channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }

        @Override
        public void closeConnectionImmediately() throws IOException {
            channel.close();
            key.cancel();
        }
    }
}
