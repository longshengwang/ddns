package org.wls.ddns.backup.socket.nio_socket;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by wls on 2019/3/28.
 */
public class Server {
    public static Logger log = Logger.getLogger(Server.class.toString());

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

    private Map<String, ProxyServer> proxyedServerMap = new HashMap<>();

//    private Map<Integer, ProxyLoopData> proxyLoopMap = new HashMap<>();
    private Map<SelectionKey, ProxyServer> keyProxyLoopMap = new HashMap<>();
    private Map<SelectionKey, Metadata>  dataInfoMap= new HashMap<>();

    public Server(int port) {
        this.proxyPort = port;
//        this.clientPort = clientPort;
    }

    public Selector getClientSelctor() {
        return clientSelector;
    }

    public Boolean addProxyServer(Integer port, SelectionKey middleKey){
        try {
            ServerSocketChannel serverChannelProxy = ServerSocketChannel.open();
            serverChannelProxy.configureBlocking(false);
            ServerSocket serverSocketProxy = serverChannelProxy.socket();
            serverSocketProxy.setReuseAddress(true);
            serverSocketProxy.bind(new InetSocketAddress(port));
            Selector proxySelector = Selector.open();
            serverChannelProxy.register(proxySelector, SelectionKey.OP_ACCEPT);

            dataInfoMap.put(middleKey, new Metadata());
            keyProxyLoopMap.put(middleKey, new ProxyServer(proxySelector, middleKey, serverChannelProxy));
            keyProxyLoopMap.get(middleKey).start();
            return true;
//            return serverSocket.getChannel().keyFor(this.selector);
        } catch (BindException e){
            System.out.println("端口(" + port+ ")已经绑定");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
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

            start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void start() {
        try {
            while (true) {
                if (selector.select(100) == 0) {
                    continue;
                }
                Iterator<SelectionKey> selectionKeys = selector.selectedKeys().iterator();
                while (selectionKeys.hasNext()) {

                    SelectionKey readyKey = selectionKeys.next();
                    selectionKeys.remove();
                    SelectableChannel selectableChannel = readyKey.channel();
//                    System.out.println("Remote Address ：" + readyKey);
//                    System.out.println("Remote Address ：" + readyKey.channel());
                    try {
                        if (readyKey.isValid() && readyKey.isAcceptable()) {

                            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectableChannel;
                            SocketChannel socketChannel = serverSocketChannel.accept();
                            registerSocketChannel(socketChannel, selector);

                        } else if (readyKey.isValid() && readyKey.isConnectable()) {
                        } else if (readyKey.isValid() && readyKey.isReadable()) {

                            if (keyProxyLoopMap.get(readyKey) == null) {
                                SocketChannel clientSocketChannel = (SocketChannel) readyKey.channel();
                                ByteBuffer contextBytes = (ByteBuffer)readyKey.attachment();
                                System.out.println("新请求的远程地址：" + clientSocketChannel.getRemoteAddress());
//                                TimeUnit.SECONDS.sleep(1);
                                int realLen = -1;
                                int proxyPort = -1;
                                if ((realLen = clientSocketChannel.read(contextBytes)) > 0) {
                                    contextBytes.flip();
                                    proxyPort = contextBytes.getInt(0);
                                    System.out.println("realLen:" + realLen + "<=========> proxyPort: " + proxyPort);
                                    contextBytes.clear();

//                                    ByteBuffer b = ByteBuffer.allocate(4096);
//                                    b.putInt(1);
//                                    b.flip();
//                                    clientSocketChannel.write(b);
                                    // 0: ok
                                    // 1: error
                                    boolean success = this.addProxyServer(proxyPort, readyKey);
                                    if(!success){
                                        log.warning("无法建立代理端口：" + proxyPort + "(应该是端口被占用了)");
                                        ByteBuffer b = ByteBuffer.allocate(4096);
                                        b.putInt(1);
                                        b.flip();
                                        clientSocketChannel.write(b);

                                        selectableChannel.close();
                                    } else {
                                        ByteBuffer b = ByteBuffer.allocate(4096);
                                        b.putInt(0);
                                        b.flip();
                                        clientSocketChannel.write(b);
                                    }


                                } else {
                                    log.warning("没有数据，那么就。。。关了吧。。");
                                    selectableChannel.close();
                                }
                                continue;

                            }

                            readSocketChannel(readyKey);

                        } else if (!readyKey.isValid()) {

                            System.out.println("============= !readyKey.isValid()");
                            this.stopProxy(readyKey);

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
        socketChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(SocketTool.PROTOCOL_BUFFER_SIZE));
    }


    private void stopProxy(SelectionKey readyKey) throws IOException {
        System.out.println("middle key被关闭了");
        ProxyServer proxyLoop = keyProxyLoopMap.get(readyKey);
        proxyLoop.closeLoop();
        proxyLoop.serverChannelProxy.close();
        keyProxyLoopMap.remove(readyKey);
        dataInfoMap.remove(readyKey);
    }

    private void readSocketChannel(SelectionKey readyKey) throws Exception {

        SocketChannel clientSocketChannel = (SocketChannel) readyKey.channel();
//        ByteBuffer contextBytes = ByteBuffer.allocate(4096);
        ByteBuffer contextBytes = (ByteBuffer)readyKey.attachment();


        int realLen = -1;
        try {
            if (clientSocketChannel.socket().isClosed()) {
                System.out.println("[IN]访问代理通道不是打开状态======《《《《");
                this.stopProxy(readyKey);
            }

            long limitLen = 0;
            if ((realLen = clientSocketChannel.read(contextBytes)) > 0) {

                contextBytes.flip();
                loopSendData(readyKey, contextBytes);
            }

            if (realLen == -1) {
                System.out.println("[IN]中间通道已经被关闭");
                readyKey.channel().close();
                this.stopProxy(readyKey);
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                this.stopProxy(readyKey);
            } catch (Exception e1) {
                e1.printStackTrace();
            }

        }
    }


    void loopSendData(SelectionKey readyKey, ByteBuffer contextBytes) throws IOException {
        Metadata dataInfo = dataInfoMap.get(readyKey);
//        System.out.println("[ loopSendData ]发送数据给外面的服务 contextBytes长度：" + contextBytes.remaining());

        if(dataInfo.isNull()){
            int protocolHeader = contextBytes.getInt();
            int indexId = protocolHeader >> Short.SIZE;
            short dataSize = (short) (protocolHeader & Short.MAX_VALUE);
//            System.out.println("[ loopSendData ] 索引ID：" + indexId + "    dataSize:" + dataSize);
            if(indexId < 0){
                System.out.println("[ loopSendData ][关闭消息] 索引ID：" + indexId + "    dataSize:" + dataSize);
                System.out.println("remain:" + contextBytes.remaining());
                contextBytes.clear();
                this.keyProxyLoopMap.get(readyKey).closeSocketByIndexId(-indexId);
                return;
            }
            dataInfo.set((int)dataSize, indexId);
        }

        SelectionKey proxyClientKey = this.keyProxyLoopMap.get(readyKey).getSelectionKey(dataInfo.getIndexId());
        if(proxyClientKey == null){
            this.keyProxyLoopMap.get(readyKey).closeSocketByIndexId(dataInfo.getIndexId());
            System.out.println("dataInfo.getRemainingLength()=====>" + dataInfo.getRemainingLength());
            byte[] remainBytes = new byte[dataInfo.getRemainingLength()];
            contextBytes.get(remainBytes);
            dataInfo.reset();
            return;
        }
        SocketChannel channel = (SocketChannel) proxyClientKey.channel();

        if(dataInfo.getRemainingLength() == contextBytes.remaining()){
            try{
                dataInfo.decrease(contextBytes.remaining());

                channel.write(contextBytes);
                if(contextBytes.hasRemaining()){
                    channel.write(contextBytes);
                }
                contextBytes.clear();
            } catch (IOException e){
                contextBytes.clear();
                dataInfo.reset();

                System.out.println("[警告]=== 我抓住了这个异常 (1  -  1) >>>>");
                e.printStackTrace();
                System.out.println("[警告]=== 我抓住了这个异常 <<<<");
            }
        }
        else if(dataInfo.getRemainingLength() > contextBytes.remaining()){
            try{
                dataInfo.decrease(contextBytes.remaining());

                channel.write(contextBytes);
                if(contextBytes.hasRemaining()){
                    channel.write(contextBytes);
                }
                contextBytes.clear();
            } catch (IOException e){
                contextBytes.clear();
                dataInfo.reset();

                System.out.println("[警告]=== 我抓住了这个异常 (1  -  1) >>>>");
                e.printStackTrace();
                System.out.println("[警告]=== 我抓住了这个异常 <<<<");
            }

        }
        else {
            System.out.println("===>>>>>>>这里会进入循环:" + dataInfo.getRemainingLength() + ";" + contextBytes.remaining());
            byte[] remainBytes = new byte[dataInfo.getRemainingLength()];
            contextBytes.get(remainBytes);

            ByteBuffer remainBuffer = ByteBuffer.allocate(dataInfo.getRemainingLength());
            remainBuffer.put(remainBytes);
            remainBuffer.flip();
            try{
                channel.write(remainBuffer);
                if(remainBuffer.hasRemaining()){
                    channel.write(remainBuffer);
                }
                dataInfo.reset();
            } catch (IOException e){
                dataInfo.reset();
                System.out.println("=== 我抓住了这个异常(1+2  -  1) >>>>");
                e.printStackTrace();
                System.out.println("=== 我抓住了这个异常 <<<<");
            }
            loopSendData(readyKey, contextBytes);



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

        Server layer = new Server(9000);
        layer.startServer();



    }
}
