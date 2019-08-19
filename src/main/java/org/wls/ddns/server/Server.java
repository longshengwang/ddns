package org.wls.ddns.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wls.ddns.SocketTool;
import org.wls.ddns.http.ProxyConfig;
import org.wls.ddns.http.HttpServerManager;
import org.wls.ddns.model.ConnModel;
import org.wls.ddns.model.Metadata;
import org.wls.ddns.model.RegisterProtocol;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.sql.Time;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Created by wls on 2019/3/28.
 */
public class Server {
    public static Logger LOG = LogManager.getLogger(Server.class);

    private static Long countStatistic = 0L;
    private int proxyPort;
    private int httpPort;
    private int clientPort;
    public long testCount = 0;
    private ServerSocketChannel serverChannel;
    private ServerSocket serverSocket;
    private Selector selector;
    private Selector clientSelector;

    private Map<SelectionKey, SelectionKey> proxyMap = new HashMap<>();
    private Map<SelectionKey, SelectionKey> clientMap = new HashMap<>();

    private Map<String, ProxyServer> proxyedServerMap = new HashMap<>();

    private Map<SelectionKey, ProxyServer> keyProxyLoopMap = new HashMap<>();
    private Map<SelectionKey, Metadata>  dataInfoMap= new HashMap<>();


    private HttpServerManager httpManager;
    private ProxyConfig proxyConfig;
    private Map<SelectionKey, ConnModel> connectionStatisticMap = new ConcurrentHashMap<>();


    public Server(int port, int httpPort) {
        this.proxyPort = port;
        this.httpPort = httpPort;
    }

    public Selector getClientSelctor() {
        return clientSelector;
    }

    public Boolean addProxyServer(/*Integer port*/RegisterProtocol protocol, SelectionKey middleKey){
        Integer port = protocol.getProxyPort();
        try {

            ServerSocketChannel serverChannelProxy = ServerSocketChannel.open();
            serverChannelProxy.configureBlocking(false);
            ServerSocket serverSocketProxy = serverChannelProxy.socket();
            serverSocketProxy.setReuseAddress(true);
            serverSocketProxy.bind(new InetSocketAddress(port));
            Selector proxySelector = Selector.open();
            serverChannelProxy.register(proxySelector, SelectionKey.OP_ACCEPT);

            dataInfoMap.put(middleKey, new Metadata(port));
            connectionStatisticMap.put(middleKey, new ConnModel(protocol));
            dataInfoMap.get(middleKey).setConnectionModel(connectionStatisticMap.get(middleKey));

            keyProxyLoopMap.put(middleKey, new ProxyServer(proxySelector, middleKey, serverChannelProxy, connectionStatisticMap.get(middleKey)));
            keyProxyLoopMap.get(middleKey).start();



            return true;
        } catch (BindException e){
            LOG.info("[Connect Error]Port(" + port+ ") is bind by other program");
        } catch (IOException e) {
            LOG.error("", e);
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

            proxyConfig = new ProxyConfig();
            proxyConfig.setStatisticMap(connectionStatisticMap);
            httpManager = new HttpServerManager(httpPort, proxyConfig);
            new Thread(httpManager).start();

            start();
        } catch (IOException e) {
            LOG.error("", e);
        }
        if(httpManager != null){
            httpManager.close();
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
                                LOG.debug("New Connect Address:" + clientSocketChannel.getRemoteAddress());
                                int realLen = -1;
                                int proxyPort = -1;
                                if ((realLen = clientSocketChannel.read(contextBytes)) > 0) {
                                    contextBytes.flip();

                                    RegisterProtocol protocol = RegisterProtocol.decode(contextBytes);
                                    if(protocol == null){
                                        LOG.warn("Protocol Read Error, Close the channel");
                                        ByteBuffer b = ByteBuffer.allocate(4096);
                                        b.putInt(2);
                                        b.flip();

                                        selectableChannel.close();
                                        continue;
                                    }
                                    LOG.info("New Register Protocol Info:");
                                    LOG.info(protocol);
//                                    proxyPort = contextBytes.getInt(0);
                                    contextBytes.clear();

                                    // 0: ok
                                    // 1: error
                                    boolean success = this.addProxyServer(protocol, readyKey);
                                    if(!success){
                                        LOG.warn("Cannot establish proxy prot:" + proxyPort + "(The port may be used)");
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
                                    LOG.warn("No data, then ... close it...");
                                    selectableChannel.close();
                                }
                                continue;

                            }

                            readSocketChannel(readyKey);

                        } else if (!readyKey.isValid()) {

                            LOG.warn("readyKey.isValid()");
                            this.stopProxy(readyKey);

                        } else if (readyKey.isValid() && readyKey.isWritable()) {
                            LOG.info("proxy ready key is write");
                        }

                    } catch (Exception e) {

                    }


                }


            }
        } catch (Exception e) {
            LOG.error("", e);

        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    LOG.error("", e);
                }
            }
        }
    }

    private static void registerSocketChannel(SocketChannel socketChannel, Selector selector) throws Exception {
        LOG.info("The register remote connection address : " + socketChannel.socket().getRemoteSocketAddress());
        socketChannel.configureBlocking(false);
        //socket SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT
        socketChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(SocketTool.PROTOCOL_BUFFER_SIZE));
    }


    private void stopProxy(SelectionKey readyKey) throws IOException {
        LOG.info("Middle Channle (middle key) is closed");
        LOG.info(connectionStatisticMap.get(readyKey));
        ProxyServer proxyLoop = keyProxyLoopMap.get(readyKey);
        proxyLoop.closeLoop();
        proxyLoop.serverChannelProxy.close();
        keyProxyLoopMap.remove(readyKey);
        dataInfoMap.remove(readyKey);
        connectionStatisticMap.remove(readyKey);
    }

    private void readSocketChannel(SelectionKey readyKey) throws Exception {

        SocketChannel clientSocketChannel = (SocketChannel) readyKey.channel();
//        ByteBuffer contextBytes = ByteBuffer.allocate(4096);
        ByteBuffer contextBytes = (ByteBuffer)readyKey.attachment();


        int realLen = -1;
        try {
            if (clientSocketChannel.socket().isClosed()) {
                LOG.warn("[IN] The proxy middle channel is not open ======《《《《");
                this.stopProxy(readyKey);
            }

            long limitLen = 0;
            if ((realLen = clientSocketChannel.read(contextBytes)) > 0) {
//                countStatistic += realLen;
                contextBytes.flip();
                loopSendData(readyKey, contextBytes);

//                LOG.warn("############# countStatistic: " + countStatistic + "############# ");
            }


            if (realLen == -1) {
                LOG.warn("The inner middle channel has be closed");
                readyKey.channel().close();
                this.stopProxy(readyKey);
            }
        } catch (Exception e) {
            LOG.error("", e);
            try {
                this.stopProxy(readyKey);
            } catch (Exception e1) {
                LOG.error("", e1);
            }

        }
    }


    void loopSendData(SelectionKey readyKey, ByteBuffer contextBytes) throws IOException {
        Metadata dataInfo = dataInfoMap.get(readyKey);
        LOG.debug("[ loopSendData ] Send the data to out service. contextBytes length:" + contextBytes.remaining());
        if(contextBytes.remaining() < 4){
            LOG.error("=======-=-==================================-=-==================================-=-==================================-=-===========================");
        }

        if(dataInfo.isNull()){

            if(dataInfo.hasNotCompleteProtocolIndex()){
                LOG.info("Find the not complete data when get the header protocol, lengh: " + dataInfo.getNotCompleteProtocolSize());
                Integer indexRemainLength = Integer.BYTES - dataInfo.getNotCompleteProtocolSize();
                if(contextBytes.remaining() < indexRemainLength){
                    LOG.error("!@#$%^&*() Fucking the network when go this step *********************************");
                }
                IntStream.range(0, indexRemainLength).forEach(i->{
                    dataInfo.putByte2NotCompleteProtocol(contextBytes.get());
                });
                int protocolHeader = dataInfo.getIndexByNotCompleteList();
                int indexId = protocolHeader >> Short.SIZE;
                short dataSize = (short) (protocolHeader & Short.MAX_VALUE);
                LOG.info("[ loopSendData ] Index ID(Twice)：" + indexId + "    dataSize:" + dataSize);
                if(indexId < 0){
                    LOG.info("[ loopSendData ][Closed Message] Index Id(Twice)：" + indexId + "    dataSize:" + dataSize);
                    contextBytes.clear();
                    this.keyProxyLoopMap.get(readyKey).closeSocketByIndexId(-indexId);
                    return;
                }
                dataInfo.set((int)dataSize, indexId);

                dataInfo.clearNotCompleteProtocolIndex();
            } else {
                if(contextBytes.remaining() < 4){
                    LOG.info("Find the length of bytebuffer is less then 4 when get protocol header, length :" + contextBytes.remaining());
                    IntStream.range(0, contextBytes.remaining()).forEach(i->{
                        dataInfo.putByte2NotCompleteProtocol(contextBytes.get());
                    });
                    contextBytes.clear();
                    return;
                } else {
                    int protocolHeader = contextBytes.getInt();
                    int indexId = protocolHeader >> Short.SIZE;
                    short dataSize = (short) (protocolHeader & Short.MAX_VALUE);
                    LOG.debug("[ loopSendData ] Index ID：" + indexId + "    dataSize:" + dataSize);
                    if(indexId < 0){
                        LOG.info("[ loopSendData ][Close Message] Index ID：" + indexId + "    dataSize:" + dataSize);
                        LOG.debug("remain:" + contextBytes.remaining());
                        contextBytes.clear();
                        this.keyProxyLoopMap.get(readyKey).closeSocketByIndexId(-indexId);
                        return;
                    }
                    dataInfo.set((int)dataSize, indexId);
                    dataInfo.clearNotCompleteProtocolIndex();
                }
            }


        }

        SelectionKey proxyClientKey = this.keyProxyLoopMap.get(readyKey).getSelectionKey(dataInfo.getIndexId());
        if(proxyClientKey == null){
            LOG.warn("The channel to outer is none(proxyClientKey is empty)");
            this.keyProxyLoopMap.get(readyKey).closeSocketByIndexId(dataInfo.getIndexId());
            //Test code, used to data statistic
            dataInfo.decrease(dataInfo.getRemainingLength());
            dataInfo.reset();
            contextBytes.clear();

            return;
        }
        SocketChannel channel = (SocketChannel) proxyClientKey.channel();

        if(dataInfo.getRemainingLength() == contextBytes.remaining()){
            try{
                dataInfo.decrease(contextBytes.remaining());

                channel.write(contextBytes);
//                if(contextBytes.hasRemaining()){
//                    channel.write(contextBytes);
//                }

                while(contextBytes.hasRemaining()) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    LOG.debug("=================================== Need repeat send ======");
                    channel.write(contextBytes);
                }

                contextBytes.clear();
            } catch (IOException e){
                contextBytes.clear();
                dataInfo.reset();

                LOG.error("[EXP WARN]=== I catch the exception (1  -  1) >>>>");
                LOG.error("", e);
                LOG.error("[EXP WARN]=== I catch the exception <<<<");
            }
        }
        else if(dataInfo.getRemainingLength() > contextBytes.remaining()){
            try{
                dataInfo.decrease(contextBytes.remaining());

                channel.write(contextBytes);
//                if(contextBytes.hasRemaining()){
//                    channel.write(contextBytes);
//                }
                while(contextBytes.hasRemaining()) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    LOG.debug("=================================== Need repeat send ======");
                    channel.write(contextBytes);
                }

                contextBytes.clear();
            } catch (IOException e){
                contextBytes.clear();
                dataInfo.reset();

                LOG.error("[EXP WARN]=== I catch the exception (2  -  2) >>>>");
                LOG.error("", e);
            }

        }
        else {
            LOG.debug("===>>>>>>> here is go loop again:" + dataInfo.getRemainingLength() + ";" + contextBytes.remaining());
            byte[] remainBytes = new byte[dataInfo.getRemainingLength()];
            contextBytes.get(remainBytes);

            ByteBuffer remainBuffer = ByteBuffer.allocate(dataInfo.getRemainingLength());
            dataInfo.decrease(contextBytes.remaining());

            remainBuffer.put(remainBytes);
            remainBuffer.flip();
            try{
                channel.write(remainBuffer);
                while(remainBuffer.hasRemaining()) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    LOG.debug("=================================== Need repeat send ======");
                    channel.write(remainBuffer);
                }
                dataInfo.reset();
            } catch (IOException e){
                dataInfo.reset();
                LOG.error("=== [EXP WARN]=== I catch the exception(1+2  -  1) >>>>");
                LOG.error("", e);
                LOG.error("=== [EXP WARN]=== I catch the exception <<<<");
            }
            loopSendData(readyKey, contextBytes);
        }
    }


    public static void main(String[] args) {
        Integer port = 9000;
        Integer httpPort = 9999;
        if(args.length > 0){
            port = Integer.parseInt(args[0]);
        }
        if(args.length > 1){
            httpPort = Integer.parseInt(args[1]);
        }
        LOG.info("Service Port is: " + port);
        LOG.info("Http Port is: " + port);
        Server layer = new Server(port, httpPort);
        layer.startServer();
    }
}
