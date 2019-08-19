package org.wls.ddns.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wls.ddns.SocketTool;
import org.wls.ddns.model.ConnModel;
import org.wls.ddns.model.RegisterProtocol;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;


public class ProxyServer extends Thread {
    public static Logger LOG = LogManager.getLogger(ProxyServer.class);


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

    private ConnModel connModel = null;

    public static Long testCount = 0L;

    public ProxyServer(Selector proxySelector, SelectionKey middleKey, ServerSocketChannel serverChannelProxy) {
        this(proxySelector, middleKey, serverChannelProxy, 4096);
    }

    public ProxyServer(Selector proxySelector, SelectionKey middleKey, ServerSocketChannel serverChannelProxy, ConnModel connModel) {
        this(proxySelector, middleKey, serverChannelProxy, 4096);
        this.connModel = connModel;
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
        middleBufferMap.put(key, ByteBuffer.allocate(SocketTool.PROTOCOL_BUFFER_SIZE));
    }

    public SelectionKey getSelectionKey(Integer indexId){
        return indexConvertMap.get(indexId);
    }

    public void closeSocketByIndexId(Integer indexId) throws IOException {
        if(indexId == null){
            return;
        }
        lock.lock();
        try {
            LOG.info("ProxyLoopData The index id is need to close: " + indexId);
            SelectionKey key = indexConvertMap.get(indexId);
            if (key != null) {
                indexConvertMap.remove(indexId);
                giveBackIndexId(indexId);
                if(key.channel() != null){
                    LOG.info("The outer connection is been close");
                    key.channel().close();
                }
                middleBufferMap.remove(key);
                keyConvertMap.remove(key);
            } else {
                LOG.warn("What the fucking: While is null");
            }
        }catch (Exception e){
            LOG.error(e.getStackTrace());
            throw e;
        } finally {

            System.out.println("indexQueue size:" + this.indexQueue.size());
            lock.unlock();
        }
    }


    public void closeSocketByKey(SelectionKey clientKey) throws IOException {
        LOG.info("[closeSocketByKey]>> client key need to close:" + clientKey);
        lock.lock();

        try{
            if(clientKey != null){
                Integer index = keyConvertMap.get(clientKey);

                LOG.info("[closeSocketByKey]>> index id:" + index);
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
            LOG.error(e.getStackTrace());
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

            if ((realLen = clientSocketChannel.read(contextBytes)) > 0) {
                contextBytes.flip();
                if(connModel != null){
                    connModel.addByteSend(realLen);
                }

                Integer index = keyConvertMap.get(clientKey);
                ByteBuffer middleBuffer = middleBufferMap.get(clientKey);

                middleBuffer.putInt(SocketTool.encodeProtocol((short)realLen, (short)index.intValue()));
                middleBuffer.put(contextBytes);
                middleBuffer.flip();

                SocketChannel serverChannel = (SocketChannel) middleKey.channel();
                serverChannel.write(middleBuffer);
                while (middleBuffer.hasRemaining()) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    serverChannel.write(middleBuffer);
                }

                contextBytes.clear();
                middleBuffer.clear();
            }
            if (realLen == -1) {
                LOG.warn("The length from outer is -1, so I close the connection");
                this.closeSocketByKey(clientKey);
            }
        } catch (Exception e) {

            LOG.error("", e);
            try {
                this.closeSocketByKey(clientKey);
                return;
            } catch (Exception e1) {
                LOG.error(e1.getStackTrace());
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
                            LOG.info("New connection is visit");

                            SocketChannel socketChannel = SocketTool.getClientChannel(selectableChannel);
                            LOG.info("Remote connection address is : " + socketChannel.getRemoteAddress());

                            if(connModel.getSecurity().equals( RegisterProtocol.SECURITY )){
                                InetSocketAddress ipAddr = (InetSocketAddress)socketChannel.getRemoteAddress();
                                String ip = ipAddr.getAddress().toString().substring(1);
                                if(!connModel.getTrustIps().contains(ip)){
                                    LOG.warn("The channel is SECURITY，IP:" + ip + " is not in the trust list, the ip need auth first!");
                                    socketChannel.close();
                                    continue;
                                }
                            }

                            SocketTool.registerSocketReaderChannel(socketChannel, selector, SocketTool.BUFFER_SIZE);

                        } else if (readyKey.isValid() && readyKey.isConnectable()) {
                        } else if (readyKey.isValid() && readyKey.isReadable()) {

                            if (keyConvertMap.get(readyKey) == null) {
                                Integer indexId = getIndexId();
                                LOG.info("===>>>>>The out index id is : "+indexId);
                                if (indexId == null) {
                                    LOG.warn("The connection has more then the max count(%d)", maxConnectionCount);
                                    readyKey.channel().close();
                                    continue;
                                }
                                setKeyIndexMap(readyKey, indexId);
                            }
                            readClientSocketChannel(readyKey);
                        } else if (!readyKey.isValid()) {
                        } else if (readyKey.isValid() && readyKey.isWritable()) {
                            LOG.info("proxy ready key is write");
                        }
                    } catch (Exception e) {

                    }
                }


            }
        } catch (IOException e) {

        }
    }
}
