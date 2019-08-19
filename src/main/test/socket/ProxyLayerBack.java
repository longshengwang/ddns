package org.wls.ddns.backup.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by wls on 2019/3/28.
 */
public class ProxyLayerBack {

    private int proxyPort;
    private int clientPort;
    public long testCount = 0;
    private ServerSocketChannel serverChannel;
    private ServerSocket serverSocket;
    private Selector selector;
    private Selector clientSelector;
    //    private DualHashBidiMap dualHashBidiMap;
    private Map<SelectionKey, SelectionKey> proxyMap = new HashMap<>();
    private Map<SelectionKey, SelectionKey> clientMap = new HashMap<>();

    public ProxyLayerBack(int port, int clientPort) {
        this.proxyPort = port;
        this.clientPort = clientPort;


    }

    public Selector getClientSelctor() {
        return clientSelector;
    }

    public void startServer() {
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverSocket = serverChannel.socket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(this.proxyPort));
            selector = Selector.open();
            clientSelector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
//            serverChannel.register(selector, SelectionKey.OP_CONNECT);

            new Thread(() -> {
                startClientLoop();
            }).start();
            start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }





    //从内部的socket读，往外面写
    public void startClientLoop() {
        try {
            while (true) {
                int keyCount = clientSelector.select(100);
                if (keyCount == 0) {
//                    System.out.println("startClientLoop key count = 0");
                    continue;
                }
//                System.out.println("startClientLoop keycount: " + keyCount);
                Iterator<SelectionKey> selecionKeys = clientSelector.selectedKeys().iterator();
//                System.out.println("================== START =============");
                while (selecionKeys.hasNext()) {
                    SelectionKey readyKey = selecionKeys.next();
                    selecionKeys.remove();

//                    System.out.println("startClientLoop readyKey.isConnectable():" + readyKey.isConnectable());
//                    System.out.println("startClientLoop readyKey.isWritable():" + readyKey.isWritable());
//                    System.out.println("startClientLoop readyKey.isAcceptable():" + readyKey.isAcceptable());
//                    System.out.println("startClientLoop readyKey.isValid():" + readyKey.isValid());
//                    System.out.println("startClientLoop readyKey.isReadable():" + readyKey.isReadable());
                    /*if(readyKey.isConnectable()){
                        System.out.println("startClientLoop is isConnectable");
                    } else {
                        System.out.println("startClientLoop is NOTTTTTTT isConnectable");

//                        SelectionKey serverKey = clientMap.get(readyKey);
//                        clientMap.remove(readyKey);
//                        proxyMap.remove(serverKey);
//                        serverKey.cancel();
//                        readyKey.cancel();
//                        continue;
                    }*/
//                    System.out.println("======>" + readyKey);
//                    System.out.println(this.clientMap.get(readyKey));
//                    System.out.println("TEST111 ] " + this.clientMap.size());
//                    System.out.println("TEST111 ] " + this.clientMap);


                    SelectableChannel clientChannel = readyKey.channel();
//                    if(readyKey.isValid() && readyKey.isAcceptable()) {
//                        ServerSocketChannel serverSocketChannel = (ServerSocketChannel)selectableChannel;
//                        SocketChannel socketChannel = serverSocketChannel.accept();
//                        registerSocketChannel(socketChannel , selector);
//
//                        Socket innerSocket = new Socket("localhost", clientPort);
//                        SocketChannel clientChannel = innerSocket.getChannel();
//                        registerSocketChannel(clientChannel, clientSelector);
//
//                        proxyMap.put(socketChannel, clientChannel);
//                        clientMap.put(clientChannel, socketChannel);

//                    } else if(readyKey.isValid() && readyKey.isConnectable()) {
//
//                    } else
//                    System.out.println("startClientLoop 33 =>");
                    if (readyKey.isValid() && readyKey.isAcceptable()) {
//                        System.out.println("startClientLoop 33 => isAcceptable");
                    } else if (readyKey.isValid() && readyKey.isWritable()) {
//                        System.out.println("startClientLoop 33 => isWritable");
//                        readyKey.
//                        readyKey.interestOps(SelectionKey.OP_READ);
                    } else if (readyKey.isValid() && readyKey.isReadable()) {
//                        System.out.println("startClientLoop 44 =>");
                        readClientChannel(readyKey);
                    } else if (!readyKey.isValid()) {
//                        System.out.println("startClientLoop 55 =>");
                        SelectionKey serverKey = clientMap.get(readyKey);
                        if(serverKey!=null){
                            proxyMap.remove(serverKey);
                            serverKey.cancel();
                        }
                        if(readyKey !=null){
                            clientMap.remove(readyKey);
                            readyKey.cancel();
                        }
                        clientChannel.close();
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
    }



    //从外面的socket读，往内部写
    public void start() {
        try {
            while (true) {
                if (selector.select(100) == 0) {
                    continue;
                }
                Iterator<SelectionKey> selecionKeys = selector.selectedKeys().iterator();
                while (selecionKeys.hasNext()) {
                    SelectionKey readyKey = selecionKeys.next();


                    selecionKeys.remove();

                    SelectableChannel selectableChannel = readyKey.channel();
                    try {
                        if (readyKey.isValid() && readyKey.isAcceptable()) {
//                            System.out.println("Server =======>isAcceptable");
                            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectableChannel;
                            SocketChannel socketChannel = serverSocketChannel.accept();
                            registerSocketChannel(socketChannel, selector);
//                        Socket innerSocket = new Socket("127.0.0.1", clientPort);

                        } else if (readyKey.isValid() && readyKey.isConnectable()) {
//                            System.out.println("Server =======>isConnectable");
                        } else if (readyKey.isValid() && readyKey.isReadable()) {
                            if (proxyMap.get(readyKey) == null) {
                                System.out.println("这是新情求");
                                SocketChannel clientChannel = SocketChannel.open();
                                Socket clientsocket = clientChannel.socket();
                                clientsocket.connect(new InetSocketAddress("localhost", clientPort));
                                registerSocketChannel(clientsocket.getChannel(), clientSelector);

                                SelectionKey clientKey = clientsocket.getChannel().keyFor(clientSelector);

                                // ===测试代
//                                System.out.println(((SocketChannel) readyKey.channel()).getRemoteAddress());
//                                ((ByteBuffer)clientKey.attachment()).flip();
//                                ((ByteBuffer)readyKey.attachment()).flip();

                                proxyMap.put(readyKey, clientKey);
                                clientMap.put(clientKey, readyKey);
                            } else {
//                                System.out.println("这是老情求");
                            }

//                            if(!selectableChannel.isOpen()){
//                                System.out.println("对方已经关闭通道了");
//                            }

                            readSocketChannel(readyKey);
//                            if(selectableChannel.isOpen()){
//                                readSocketChannel(readyKey);
//                            } else {
//                                System.out.println("对方已经关闭通道了");
//                            }

                        } else if (!readyKey.isValid()) {
                            System.out.println("Server =======>not isValid");
                            System.out.println("====== invalid");
                            SelectionKey clientKey = proxyMap.get(readyKey);
                            proxyMap.remove(readyKey);
                            clientMap.remove(clientKey);

                            clientKey.channel().close();
                            readyKey.channel().close();

                            clientKey.cancel();
                            readyKey.cancel();
//                        clientChannel.close();
//                        selectableChannel.close();
                        } else if (readyKey.isValid() && readyKey.isWritable()) {
                            System.out.println("Server =======>isWritable");
                            System.out.println("proxy ready key is write");
//                        readyKey.interestOps(SelectionKey.OP_WRITE);
                        }

                    } catch (Exception e) {

                    }


                }


            }
        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    private static void registerSocketChannel(SocketChannel socketChannel, Selector selector) throws Exception {
        System.out.println("registerSocketChannel is " + socketChannel.socket().getRemoteSocketAddress());
        socketChannel.configureBlocking(false);
        //socket通道可以且只可以注册三种事件SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT
        socketChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(40960));
    }

    private void readSocketChannel(SelectionKey readyKey) throws Exception {

        SocketChannel clientSocketChannel = (SocketChannel) readyKey.channel();
//        ByteBuffer contextBytes = ByteBuffer.allocate(4096);
        ByteBuffer contextBytes = (ByteBuffer)readyKey.attachment();


        int realLen = -1;
        try {
            if (clientSocketChannel.socket().isClosed()) {
                System.out.println("[IN]访问代理通道不是打开状态======《《《《");
                SelectionKey clientKey = proxyMap.get(readyKey);
                proxyMap.remove(readyKey);
                clientMap.remove(clientKey);

                clientKey.channel().close();
                readyKey.channel().close();

                clientKey.cancel();
                readyKey.cancel();
            }

            long limitLen = 0;
//            long t1 = System.currentTimeMillis();
            if ((realLen = clientSocketChannel.read(contextBytes)) > 0) {
//                limitLen += realLen;
//                testCount += realLen;

                contextBytes.flip();
//                byte[] messageBytes = Arrays.copyOfRange(contextBytes.array(), 0, realLen);
                SelectionKey clientKey = this.proxyMap.get(readyKey);
                SocketChannel channel = (SocketChannel) clientKey.channel();
//                ByteBuffer bf = ByteBuffer.wrap(messageBytes);
                if(contextBytes.hasRemaining()){
                    channel.write(contextBytes);
                }
                contextBytes.clear();
            }

//            long t2 = System.currentTimeMillis();
//            System.out.println("【IN】单次轮询的事件是：" + (t2-t1) + " 毫秒"+ "  大小是：" + limitLen);
//            System.out.println("【IN】testCount 大小是：" + testCount);

            if (realLen == -1) {
//                System.out.println("[IN]访问代理通道已经关闭(因为len -1 )");
                SelectionKey clientKey = proxyMap.get(readyKey);
                if(clientKey != null){
                    clientKey.cancel();
                    clientKey.channel().close();
                }
                if(readyKey != null){
                    readyKey.cancel();
                    readyKey.channel().close();
                }
                proxyMap.remove(readyKey);
                clientMap.remove(clientKey);

//                clientKey.channel().close();
//                readyKey.channel().close();
//
//                clientKey.cancel();
//                readyKey.cancel();
            }
        } catch (Exception e) {
//            System.out.println("访问代理通道已经关闭");
            e.printStackTrace();

            try {
                SelectionKey clientKey = proxyMap.get(readyKey);
                proxyMap.remove(readyKey);
                clientMap.remove(clientKey);
                if (clientKey != null) {
                    clientKey.cancel();
                    clientKey.channel().close();
                }
                if (readyKey != null) {
                    readyKey.cancel();
                    readyKey.channel().close();
                }
            } catch (Exception e1) {
                e1.printStackTrace();
            }

        }
    }


    private void readClientChannel(SelectionKey readyKey) throws Exception {

        SocketChannel clientSocketChannel = (SocketChannel) readyKey.channel();
        ByteBuffer contextBytes = (ByteBuffer) readyKey.attachment();
//        ByteBuffer contextBytes = ByteBuffer.allocate(4096);

        int realLen = -1;
        try {

            SelectionKey serverKey = this.clientMap.get(readyKey);

            if (!clientSocketChannel.isConnected() || clientSocketChannel.socket().isClosed()) {

                if (serverKey != null) {
                    proxyMap.remove(serverKey);
                    serverKey.cancel();
                    serverKey.channel().close();
                }
                if (readyKey != null) {
                    clientMap.remove(readyKey);
                    readyKey.cancel();
                    readyKey.channel().close();
                }
                return;
            }

            if(clientSocketChannel.socket().isClosed()){
                System.out.println("[readClientChannel] clientSocketChannel.socket().isClosed()" + clientSocketChannel.socket().isClosed());
            }

            long limitLen = 0;
            long t1 = System.currentTimeMillis();

//            System.out.println("==TEST=>contextBytes" + contextBytes.remaining());
//            if(!contextBytes.hasRemaining()){
//                contextBytes.clear();
//            } else {
//
//                SocketChannel serverChannel = (SocketChannel) serverKey.channel();
//                if(!serverChannel.socket().isClosed()){
//                serverChannel.write(contextBytes);
//                if(contextBytes.remaining()==0){
//                    contextBytes.clear();
//                }
//                return;
//            }
            if ((realLen = clientSocketChannel.read(contextBytes)) > 0) {
                contextBytes.flip();
                byte[] messageBytes = Arrays.copyOfRange(contextBytes.array(), 0, realLen);

                SocketChannel serverChannel = (SocketChannel) serverKey.channel();
//                serverChannel.write(contextBytes);

                ByteBuffer bf =  ByteBuffer.wrap(messageBytes);
                while(bf.hasRemaining()){
                    serverChannel.write(bf);
                }

                contextBytes.clear();

            }
            long t2 = System.currentTimeMillis();
//            System.out.println("【OUT】单次轮询的事件是：" + (t2-t1) + " 毫秒" + "  大小是：" + limitLen);
//            System.out.println("realLen is" + realLen );
            if (realLen == -1) {
//                System.out.println("CLIENT 访问代理通道已经关闭(因为len -1 )");
//                if(clientSocketChannel.socket().isClosed()) {
                    try {
//                        SelectionKey serverKey = this.clientMap.get(readyKey);

                        if (serverKey != null) {
                            proxyMap.remove(serverKey);
                            serverKey.cancel();
                            serverKey.channel().close();
                        }
                        if (readyKey != null) {
                            clientMap.remove(readyKey);
                            readyKey.cancel();
                            readyKey.channel().close();
                        }
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
//                }
            }
        } catch (Exception e) {


//            System.out.println("CLIENT 访问代理通道已经关闭");
//            System.out.println("clientSocketChannel.socket().isClosed::"+clientSocketChannel.socket().isClosed());

            e.printStackTrace();
//            if(clientSocketChannel.socket().isClosed()){
                try {
                    SelectionKey serverKey = this.clientMap.get(readyKey);

                    if (serverKey != null) {
                        proxyMap.remove(serverKey);
                        serverKey.cancel();
                        serverKey.channel().close();
                    }
                    if (readyKey != null) {
                        clientMap.remove(readyKey);
                        readyKey.cancel();
                        readyKey.channel().close();
                    }
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
//            }


        }
    }

    public static void main(String[] args) {
//        new Thread(){
//            @Override
//            public void run() {
//                ProxyLayer layer = new ProxyLayer(9001, 5201);
//                layer.startServer();
//            }
//        }.start();

        ProxyLayerBack layer = new ProxyLayerBack(9000, 80);
        layer.startServer();



    }
}
