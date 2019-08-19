package org.wls.ddns.backup.socket.nio_socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.IntStream;


public class ProxyServer extends Thread {
    public static Logger log = Logger.getLogger(ProxyServer.class.toString());


    public Selector selector;
    private SelectionKey middleKey;
    private Boolean isRun = true;
    public ServerSocketChannel serverChannelProxy;
    private AtomicInteger index = new AtomicInteger(1);
    private Map<SelectionKey, Integer> keyConvertMap = new ConcurrentHashMap<>();
    private Map<Integer, SelectionKey> indexConvertMap = new ConcurrentHashMap<>();
    private Lock lock = new ReentrantLock();

    private Map<SelectionKey, ByteBuffer> middleBufferMap = new ConcurrentHashMap<>();

    private Integer maxConnectionCount = 4096;
    private Integer bufferSize;
    private Queue<Integer> indexQueue = new ConcurrentLinkedQueue<>();

    public static Long testCount = 0L;

    public ProxyServer(Selector proxySelector, SelectionKey middleKey, ServerSocketChannel serverChannelProxy) {
        this(proxySelector, middleKey, serverChannelProxy, 4096);
    }

    public ProxyServer(Selector proxySelector, SelectionKey middleKey, ServerSocketChannel serverChannelProxy, Integer maxConnectionCount) {
        this.selector = proxySelector;
        this.middleKey = middleKey;
        this.maxConnectionCount = maxConnectionCount;
        this.bufferSize = SocketTool.BUFFER_SIZE;
        this.serverChannelProxy = serverChannelProxy;
        initializeQueue();
    }

    private void initializeQueue() {
        IntStream.range(1, maxConnectionCount + 1).forEach(i -> indexQueue.offer(i));
    }

    public void closeLoop() {
        this.isRun = false;
    }

    private Integer getIndexId() {
        return this.indexQueue.poll();
    }

    private void giveBackIndexId(Integer index) {
        this.indexQueue.offer(index);
    }


    private void setKeyIndexMap(SelectionKey key, Integer index) {
        keyConvertMap.put(key, index);
        indexConvertMap.put(index, key);
//        System.out.println("这是set:" +this.indexConvertMap);
        middleBufferMap.put(key, ByteBuffer.allocate(SocketTool.PROTOCOL_BUFFER_SIZE));
    }

//    private void readMiddleSocketChannel(SelectionKey middleKey){
//
//    }
    public SelectionKey getSelectionKey(Integer indexId){
//        System.out.println("这 get:" +this.indexConvertMap);
        return indexConvertMap.get(indexId);
    }

    public void closeSocketByIndexId(Integer indexId) throws IOException {
        if(indexId == null){
            return;
        }
        lock.lock();
        try {
            log.info("[ProxyServer]ProxyLoopData需要关闭的indexid:" + indexId);
//            System.out.println("[ProxyServer]ProxyLoopData需要关闭的indexid:" + indexId);
            SelectionKey key = indexConvertMap.get(indexId);
            if (key != null) {
                indexConvertMap.remove(indexId);
                if(key.channel() != null){
                    System.out.println("[ProxyServer]外部的连接被执行关闭");
                    key.channel().close();
                }
                middleBufferMap.remove(key);
                keyConvertMap.remove(key);
            } else {
                System.out.println("卧槽： 怎么是null了");
                System.out.println();
                System.out.println();
            }
        }catch (Exception e){
            e.printStackTrace();
            throw e;
        } finally {
            giveBackIndexId(indexId);
            lock.unlock();
        }

//        keyConvertMap.remove(key);
//        indexConvertMap.remove(indexId);
//        middleBufferMap.remove(key);
//        giveBackIndexId(indexId);
    }


    public void closeSocketByKey(SelectionKey clientKey) throws IOException {
        System.out.println("[closeSocketByKey]ProxyLoopData需要关闭client key:" + clientKey);
        lock.lock();

        try{
            if(clientKey != null){
                Integer index = keyConvertMap.get(clientKey);

                System.out.println("[closeSocketByKey]>> index id:" + index);
                if(index != null){
                    closeRemoteSocket(index);
                    indexConvertMap.remove(index);
                    giveBackIndexId(index);
                }
                keyConvertMap.remove(clientKey);
                middleBufferMap.remove(clientKey);

                if(clientKey.channel() != null){
                    clientKey.channel().close();
                }

            }
        } catch (Exception e){
            e.printStackTrace();
            throw e;
        } finally {
            lock.unlock();
        }
    }

    private void closeRemoteSocket(Integer index) throws IOException{
//        try{
//            System.out.println("[发起关闭消息] index id" + index);
//            ByteBuffer closedBuffer = ByteBuffer.allocate(SocketTool.PROTOCOL_BUFFER_SIZE);
//
//            int closeId = -index;
//            closedBuffer.putInt(SocketTool.encodeProtocol((short)0, (short)closeId));
//            closedBuffer.flip();
//
//            SocketChannel serverChannel = (SocketChannel) middleKey.channel();
//            serverChannel.write(closedBuffer);
//            while (closedBuffer.hasRemaining()) {
//                serverChannel.write(closedBuffer);
//            }
//        } catch (Exception e){
//            System.out.println("发送关闭的时候发生错误");
//            throw e;
//        }


    }

    private void readClientSocketChannel(SelectionKey clientKey) {
        SocketChannel clientSocketChannel = (SocketChannel) clientKey.channel();
        ByteBuffer contextBytes = (ByteBuffer) clientKey.attachment();

        int realLen = -1;
        try {

//            if (!clientSocketChannel.isConnected() || clientSocketChannel.socket().isClosed()) {
//                //TODO 需要告诉另一端，通知对端关闭
//                this.closeSocketByKey(clientKey);
//                return;
//            }

//            if (clientSocketChannel.socket().isClosed()) {
//                System.out.println("[readClientChannel] clientSocketChannel.socket().isClosed()" + clientSocketChannel.socket().isClosed());
//            }
//            System.out.println("-----------test: 1");
            if ((realLen = clientSocketChannel.read(contextBytes)) > 0) {
//                testCount += realLen;
//                System.out.println("-----------test realLen:" + realLen);
                contextBytes.flip();

                Integer index = keyConvertMap.get(clientKey);
                ByteBuffer middleBuffer = middleBufferMap.get(clientKey);

//                middleBuffer.putShort((short)index.intValue());
                middleBuffer.putInt(SocketTool.encodeProtocol((short)realLen, (short)index.intValue()));
                middleBuffer.put(contextBytes);
                middleBuffer.flip();

                SocketChannel serverChannel = (SocketChannel) middleKey.channel();
                serverChannel.write(middleBuffer);
                while (middleBuffer.hasRemaining()) {
                    serverChannel.write(middleBuffer);
                }

                contextBytes.clear();
                middleBuffer.clear();
            }
            if (realLen == -1) {
//                this.closeSocketByIndexId();
                this.closeSocketByKey(clientKey);
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                this.closeSocketByKey(clientKey);
                return;
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        try {
            while (isRun) {
                if (selector.select(100) == 0) {
                    continue;
                }
                Iterator<SelectionKey> selectionKeys = selector.selectedKeys().iterator();
                while (selectionKeys.hasNext()) {
                    SelectionKey readyKey = selectionKeys.next();
                    selectionKeys.remove();

                    SelectableChannel selectableChannel = readyKey.channel();
                    try {
                        if (readyKey.isValid() && readyKey.isAcceptable()) {

                            SocketChannel socketChannel = SocketTool.getClientChannel(selectableChannel);
                            SocketTool.registerSocketReaderChannel(socketChannel, selector, SocketTool.BUFFER_SIZE);

                        } else if (readyKey.isValid() && readyKey.isConnectable()) {
                        } else if (readyKey.isValid() && readyKey.isReadable()) {

                            if (keyConvertMap.get(readyKey) == null) {
//                                System.out.println(getIndexId());
                                Integer indexId = getIndexId();
                                System.out.println("===>>>>>从外面过来的indexID:"+indexId);
                                if (indexId == null) {
                                    System.out.printf("The connection has more then the max count(%d)", maxConnectionCount);
                                    readyKey.channel().close();
                                    continue;
                                }
                                setKeyIndexMap(readyKey, indexId);
                            }
                            readClientSocketChannel(readyKey);
//                            System.out.println("总共流量是:"+ testCount);

                        } else if (!readyKey.isValid()) {
                            /*System.out.println("Server =======>not isValid");
                            System.out.println("====== invalid");
                            SelectionKey clientKey = proxyMap.get(readyKey);
                            proxyMap.remove(readyKey);
                            clientMap.remove(clientKey);

                            clientKey.channel().close();
                            readyKey.channel().close();

                            clientKey.cancel();
                            readyKey.cancel();*/
                        } else if (readyKey.isValid() && readyKey.isWritable()) {
                            System.out.println("Server =======>isWritable");
                            System.out.println("proxy ready key is write");
                        }

                    } catch (Exception e) {

                    }


                }


            }
        } catch (IOException e) {

        }

    }

    public static void main(String[] args) {
//        IntStream.range(1, 12).forEach(i -> System.out.println(i));
        Queue<Integer> indexQueue = new ConcurrentLinkedQueue<>();
        System.out.println(indexQueue.poll());
    }
}
