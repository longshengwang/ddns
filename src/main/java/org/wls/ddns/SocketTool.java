package org.wls.ddns;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;

/**
 * Created by wls on 2019/8/1.
 */
public class SocketTool {

    public static Integer BUFFER_SIZE = 4096;
    //多一个int
    public static Integer PROTOCOL_BUFFER_SIZE = 4096 + 4;

    public static SocketChannel getClientChannel(SelectableChannel selectableChannel) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectableChannel;
        SocketChannel socketChannel = serverSocketChannel.accept();
        return socketChannel;
    }

    public static void registerSocketChannel(SocketChannel socketChannel,
                                              Selector selector,
                                              Integer bufferSize,
                                              Boolean isBlocking,
                                              int event) throws IOException, ClosedChannelException {
        socketChannel.configureBlocking(isBlocking);
        //socket通道可以且只可以注册三种事件SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT
        socketChannel.register(selector, event, ByteBuffer.allocate(bufferSize));
    }

    public static void registerSocketReaderChannel(SocketChannel socketChannel,
                                             Selector selector,
                                             Integer bufferSize) throws IOException, ClosedChannelException {
        registerSocketChannel(socketChannel, selector,bufferSize, false, SelectionKey.OP_READ);
    }

    public static int encodeProtocol(short len, short index){
        int protocolInt = 0;
        return index << Short.SIZE | len;
    }

    public static short[] decodeProtocol(int protocolHeader){
        short len = (short) (protocolHeader >> Short.SIZE);
        short index = (short) (protocolHeader & Short.MAX_VALUE);
        short[] s = {len, index};
        return s;
    }
}
