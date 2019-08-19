package org.wls.ddns.client;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wls.ddns.SocketTool;
import org.wls.ddns.model.Metadata;
import org.wls.ddns.model.RegisterProtocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;


/**
 * Created by wls on 2019/3/28.
 */
public class Client implements Runnable {
    public static Logger LOG = LogManager.getLogger(Client.class);

    private String remoteIp;
    private Integer remotePort;
    private Integer proxyPort;
    private String localIp;
    private Integer localPort;
    private String secretKey;
    private String name;
    private Integer security = RegisterProtocol.NO_SECURITY;

    private static Long countStatistic = 0L;


    private Selector localSelector;
    private Selector remoteSelector;
    //    private SocketChannel clientChannel= null;
    private SocketChannel remoteChannel = null;
    private Map<Integer, SelectionKey> indexProxyMap = new ConcurrentHashMap<>();
    private Map<SelectionKey, Integer> keyProxyMap = new ConcurrentHashMap<>();
    private Map<SelectionKey, ByteBuffer> keyByteBufferMap = new ConcurrentHashMap<>();
    private Map<SelectionKey, Metadata> dataInfoMap = new HashMap<>();
    private Lock connectionLock = new ReentrantLock();
    private Lock clientStopLock = new ReentrantLock();

    public static Long testCount = 0L;

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSecurity(Integer security){
        this.security = security;
    }

    public Client(String remoteIp, Integer remotePort, Integer proxyPort, String localIp, Integer localPort, String secretKey, String name) {
        this(remoteIp, remotePort, proxyPort, localIp, localPort);
        this.name = name;
        this.secretKey = secretKey;

    }

    public Client(String remoteIp, Integer remotePort, Integer proxyPort, String localIp, Integer localPort) {
        this.localPort = localPort;
        this.remotePort = remotePort;
        this.remoteIp = remoteIp;
        this.localIp = localIp;
        this.proxyPort = proxyPort;
    }

    private static void registerSocketChannel(SocketChannel socketChannel, Selector selector, Integer bufferSize) throws IOException {
        LOG.info("registerSocketChannel is " + socketChannel.socket().getRemoteSocketAddress());
        socketChannel.configureBlocking(false);
        //socket通道可以且只可以注册三种事件SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT
        socketChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(bufferSize));
    }


    private boolean initLocalConnect() {
        try {
            this.localSelector = Selector.open();
            return true;
        } catch (IOException e) {
            LOG.error("", e);
        }
        return false;
    }

    private void addLocalConnect(Integer indexId) throws IOException {

        LOG.info("[Add connection to local service][Start] indexID:" + indexId);
        ByteBuffer middleBuffer = ByteBuffer.allocate(SocketTool.PROTOCOL_BUFFER_SIZE);
        SocketChannel clientChannel = SocketChannel.open();
        Socket clientSocket = clientChannel.socket();
        clientSocket.connect(new InetSocketAddress(this.localIp, this.localPort));
        registerSocketChannel(clientSocket.getChannel(), this.localSelector, SocketTool.BUFFER_SIZE);

        SelectionKey clientKey =  clientSocket.getChannel().keyFor(this.localSelector);
        LOG.info("[Add connection to local service] Get the Key[TEST FOR DELAY]");
        indexProxyMap.put(indexId, clientKey);
        keyProxyMap.put(clientKey, indexId);
        keyByteBufferMap.put(clientKey, middleBuffer);
        LOG.info("[Add connection to local service][END] indexID:" + indexId);
    }

    private boolean initRemoteConnect() {
        Socket remoteSocket = null;
        try {
            this.remoteSelector = Selector.open();
            remoteChannel = SocketChannel.open();
            remoteSocket = remoteChannel.socket();
            remoteSocket.connect(new InetSocketAddress(this.remoteIp, this.remotePort));
            registerSocketChannel(remoteSocket.getChannel(), this.remoteSelector, SocketTool.PROTOCOL_BUFFER_SIZE); //暂时都用一个selector
            SelectionKey middleKey = remoteSocket.getChannel().keyFor(remoteSelector);

            RegisterProtocol protocol = new RegisterProtocol(localPort, proxyPort, name, secretKey, security);
            // tell the protocol to server
            ByteBuffer protocolBuffer = protocol.encode();
            remoteChannel.write(protocolBuffer);

            while (protocolBuffer.hasRemaining()) {
                remoteChannel.write(protocolBuffer);
            }

            // Need to wait server's response, server will tell client the proxy port is open successfully
            ByteBuffer o = (ByteBuffer) middleKey.attachment();
            int len = 0;
            // 0: ok
            // 1: error
            // 2: not correct secret key;
            while ((len = remoteChannel.read(o)) == 0) {
                Thread.sleep(100);
            }
            o.flip();
            int status = o.getInt(0);
            o.clear();
            if (status == 0) {
                LOG.info("Remote Proxy port is open successfully");
                return true;
            }
//            return true;
        } catch (IOException e) {
            LOG.error("", e);
        } catch (InterruptedException e) {
            LOG.error("", e);
        }

        return false;
    }

    //local connection tell me to close
    private void stopClient(SelectionKey readyKey) throws IOException {


        Integer indexId = this.keyProxyMap.get(readyKey);
        LOG.warn("[STOP WARN]stopClient(SelectionKey readyKey): indexid: " + indexId + " >>>>>>>>");
        try {
            ByteBuffer bf = this.keyByteBufferMap.get(readyKey);
            if (bf == null) {
                LOG.warn("bytebuffer has been clear");
                return;
            }
            sendCloseFlag2Middle(indexId);
            readyKey.channel().close();
            LOG.warn("[STOP WARN]stopClient(SelectionKey readyKey) : indexid: " + indexId + " <<<<<<<<");
        } catch (IOException e) {
            throw e;
        } finally {
            this.keyByteBufferMap.remove(readyKey);
            this.keyProxyMap.remove(indexId);
            this.indexProxyMap.remove(indexId);
        }


    }

    private void stopClientWithoutSendMessage(SelectionKey readyKey) throws IOException {


        Integer indexId = this.keyProxyMap.get(readyKey);
        LOG.warn("[STOP WARN]stopClient(SelectionKey readyKey): indexid: " + indexId + " >>>>>>>>");
        try {
            ByteBuffer bf = this.keyByteBufferMap.get(readyKey);
            if (bf == null) {
                LOG.warn("bytebuffer has been clear");
                return;
            }
//            sendCloseFlag2Middle(indexId);
            readyKey.channel().close();
            LOG.warn("[STOP WARN]stopClient(SelectionKey readyKey) : indexid: " + indexId + " <<<<<<<<");
        } catch (IOException e) {
            throw e;
        } finally {
            this.keyByteBufferMap.remove(readyKey);
            this.keyProxyMap.remove(indexId);
            this.indexProxyMap.remove(indexId);
        }


    }

    private void clearAll() {

    }

    //middle connection tell me to close
    private void stopClient(Integer indexId) throws IOException {
//        clientStopLock.lock();
        try {
            LOG.warn("[STOP WARN]client stop client indexID:" + indexId);
            SelectionKey key = this.indexProxyMap.get(indexId);

            if (key != null) {
                if (key.channel() != null) {
                    key.channel().close();
                }
                this.keyByteBufferMap.remove(key);
//            this.dataInfoMap.remove(key);
            }

            this.keyProxyMap.remove(indexId);
            this.indexProxyMap.remove(indexId);
        } catch (Exception e) {
            throw e;
        } finally {
//            clientStopLock.unlock();
        }

    }

    private void sendCloseFlag2Middle(Integer index) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        int id = index.intValue();

        short closeId = (short) -id;
//        buffer.putShort(closeId);
        buffer.putInt(SocketTool.encodeProtocol((short) 0, closeId));
        buffer.flip();

        this.remoteChannel.write(buffer);
        while (buffer.hasRemaining()) {
            this.remoteChannel.write(buffer);
        }
//        buffer.clear();
    }

    //UPPER DATA
    private void getDataFromLocal(SelectionKey localKey, SocketChannel readChannel, SocketChannel writeChannel) {
//        LOG.info("====== getDataFromLocal");
        ByteBuffer byteBuffer = (ByteBuffer) localKey.attachment();
        ByteBuffer middleBuffer = keyByteBufferMap.get(localKey);
        int count = 0;
        while (middleBuffer == null && count < 10) {
            LOG.warn("[UPPER DATA]The tmp buffer has not been create, sleep 100 ms");
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                LOG.error(e.getMessage());
            }
            middleBuffer = keyByteBufferMap.get(localKey);
            count++;
        }
        try {
            int len = 0;
            if ((len = readChannel.read(byteBuffer)) > 0) {

//                LOG.debug("[UPPER DATA]data length：" + len);
                byteBuffer.flip();

                middleBuffer.putInt(SocketTool.encodeProtocol((short) len, (short) keyProxyMap.get(localKey).intValue()));
                middleBuffer.put(byteBuffer);
                middleBuffer.flip();
                writeChannel.write(middleBuffer);
                while (middleBuffer.hasRemaining()) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    LOG.info("=================================== Need repeat send ======");
                    writeChannel.write(middleBuffer);
                }
                byteBuffer.clear();
                middleBuffer.clear();

//                countStatistic += (len + 4);
//                LOG.warn("################ 已上传的数据总量:" + countStatistic + "################");
            }
            if (len == -1) {
                LOG.info("[UPPER DATA]client data has ALL return indexid====》》》");
                stopClientWithoutSendMessage(localKey);
            }
        } catch (IOException e) {
            LOG.error("", e);
            LOG.error("[UPPER DATA]client close the connection, throw exception");
            try {
                stopClient(localKey);
            } catch (IOException e1) {
                LOG.error("[UPPER DATA]Close the connection again ========<><><><><><");
            }

        }

    }

    void loopParseMiddleData(SelectionKey serverKey, ByteBuffer byteBuffer) throws IOException {
//        LOG.debug("key:" + serverKey + ".... bytebuffer remain:" + byteBuffer.remaining());
        Metadata dataInfo = dataInfoMap.get(serverKey);
        if (byteBuffer.remaining() == 0) {
            LOG.debug("[DOWN DATA]remain is empty");
            byteBuffer.clear();
            return;
        }
        if (dataInfo.isNull()) {
            Integer protocolHeader;
            if(dataInfo.hasNotCompleteProtocolIndex()){
                LOG.info("Find the not complete data when get the header protocol, length: " + dataInfo.getNotCompleteProtocolSize());
                Integer indexRemainLength = Integer.BYTES - dataInfo.getNotCompleteProtocolSize();
                if(byteBuffer.remaining() < indexRemainLength){
                    LOG.error("!@#$%^&*() Fucking the network when go this step *********************************");
                }
                IntStream.range(0, indexRemainLength).forEach(i->{
                    dataInfo.putByte2NotCompleteProtocol(byteBuffer.get());
                });
                protocolHeader = dataInfo.getIndexByNotCompleteList();
            } else {
                if(byteBuffer.remaining() < 4){
                    LOG.info("Find the length of bytebuffer is less then 4 when get protocol header, length :" + byteBuffer.remaining());
                    IntStream.range(0, byteBuffer.remaining()).forEach(i->{
                        dataInfo.putByte2NotCompleteProtocol(byteBuffer.get());
                    });
                    byteBuffer.clear();
                    return;
                } else {
                    protocolHeader = byteBuffer.getInt();
                }
            }

            int indexId = protocolHeader >> Short.SIZE;
            short dataSize = (short) (protocolHeader & Short.MAX_VALUE);
            if (indexId < 0) {
//                LOG.info("[DOWN DATA] [关闭消息]对端通知关闭了 indexId:" + indexId);
                int realIndexId = -indexId;
                stopClient(realIndexId);
                loopParseMiddleData(serverKey, byteBuffer);
                return;
            }

            if (indexProxyMap.get(indexId) == null) {
                try {
                    addLocalConnect(indexId);
                } catch (IOException e) {
                    LOG.warn("[Connection ERROR]Connect to inner service error(Port is not running)");
                    sendCloseFlag2Middle(indexId);
                    // drop the data - must
                    IntStream.range(0, dataSize).forEach(i -> byteBuffer.get());
                    loopParseMiddleData(serverKey, byteBuffer);
                    return;
                }

            }
            dataInfo.set((int) dataSize, indexId);
        }


        SelectionKey key = indexProxyMap.get(dataInfo.getIndexId());
        if (key == null) {
            //表示连接内部的连接已经断开，那么需要清除dataInfo，还有当前的缓存也需要clear
            LOG.warn("[DOWN DATA]key is empty, then close it ");
//            stopClient(key);
            stopClient(dataInfo.getIndexId());
            dataInfo.reset();
            //clear the data which is prepare to send inner service
            byte[] remainBytes = new byte[dataInfo.getRemainingLength()];
            byteBuffer.get(remainBytes);
            loopParseMiddleData(serverKey, byteBuffer);
            return;
        }
        SocketChannel writeChannel = (SocketChannel) key.channel();
        if (dataInfo.getRemainingLength() == byteBuffer.remaining()) {
            try {
                dataInfo.decrease(byteBuffer.remaining());

                writeChannel.write(byteBuffer);
                while (byteBuffer.hasRemaining()) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    writeChannel.write(byteBuffer);
                }
            } catch (IOException e) {
                LOG.error("[Down Data][WARN]=== [EXP WARN]=== I catch the exception (1  -  1) >>>>");
                LOG.error("", e);
                LOG.error("[Down Data][WARN]=== [EXP WARN]=== I catch the exception <<<<<");
                stopClient(key);
                dataInfo.reset();
            } finally {
                byteBuffer.clear();
            }

        } else if (dataInfo.getRemainingLength() > byteBuffer.remaining()) {
            try {
                dataInfo.decrease(byteBuffer.remaining());

                writeChannel.write(byteBuffer);
                while (byteBuffer.hasRemaining()) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    writeChannel.write(byteBuffer);
                }
            } catch (IOException e) {
                LOG.error("", e);
                stopClient(key);
            } finally {
                byteBuffer.clear();
            }

        } else {
//            System.out.println("[Down数据]===>>>>>>>这里会进入循环:" + dataInfo.getRemainingLength() + ";" + byteBuffer.remaining());
            byte[] remainBytes = new byte[dataInfo.getRemainingLength()];
            byteBuffer.get(remainBytes);

            ByteBuffer remainBuffer = ByteBuffer.allocate(dataInfo.getRemainingLength());
            remainBuffer.put(remainBytes);
            remainBuffer.flip();
            try {
                writeChannel.write(remainBuffer);
                while (remainBuffer.hasRemaining()) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    writeChannel.write(remainBuffer);
                }
                dataInfo.reset();
            } catch (IOException e) {
                LOG.error("", e);
//                stopClient(key);
                dataInfo.reset();
            } finally {
                loopParseMiddleData(serverKey, byteBuffer);
            }
        }


    }


    //DOWN DATA
    private void getDataFromMiddle(SelectionKey serverKey, SocketChannel readChannel) throws IOException {
//        LOG.info("进入到解析的流程了");
        if (dataInfoMap.get(serverKey) == null) {
            dataInfoMap.put(serverKey, new Metadata());
        }
        ByteBuffer byteBuffer = (ByteBuffer) serverKey.attachment();
        if (byteBuffer.remaining() == 0) {
            LOG.warn("bytebuffer remaining is empty, munual close(ugly)");
            byteBuffer.clear();
        }

        int len = 0;
        if ((len = readChannel.read(byteBuffer)) > 0) {
            byteBuffer.flip();
            loopParseMiddleData(serverKey, byteBuffer);
        }

        if (len == -1) {
            LOG.warn("[Down data]middle channel has been closed");
            serverKey.channel().close();
            clearAll();
        }


        /*ByteBuffer byteBuffer = (ByteBuffer) readyKey.attachment();
        int len = 0;
        if((len = readChannel.read(byteBuffer)) > 0){
            byteBuffer.flip();
            int protocolHeader = byteBuffer.getInt();
            int indexId = protocolHeader >> Short.SIZE;
            int dataSize = protocolHeader & Short.MAX_VALUE;

            if(indexId < 0){
                //middle tell me to close local connect
                int realIndexId = -indexId;
                stopClient(realIndexId);
                return;
            }
            if(indexProxyMap.get(indexId) == null){
                addLocalConnect(indexId);
            }

            SocketChannel writeChannel = (SocketChannel)indexProxyMap.get(indexId).channel();
            writeChannel.write(byteBuffer);
            while(byteBuffer.hasRemaining()){
                writeChannel.write(byteBuffer);
            }
            byteBuffer.clear();
        }
        if(len == -1){
            System.out.println("middle 通道已经断开了");
            readyKey.channel().close();
            clearAll();
        }*/
    }

    @Override
    public void run() {
        initLocalConnect();
        if (!initRemoteConnect()) {
            LOG.error("[ERROR]Initial ERROR，Some errors come from server");
            return;
        }

        new Thread() {
            public void run() {
                startClientProxyLoop();
            }
        }.start();


        try {
            while (true) {
                int keyCount = this.remoteSelector.select(100);
                if (keyCount == 0) {
                    continue;
                }
                Iterator<SelectionKey> selecionKeys = this.remoteSelector.selectedKeys().iterator();

                while (selecionKeys.hasNext()) {
                    SelectionKey readyKey = selecionKeys.next();
                    selecionKeys.remove();
                    SocketChannel channel = (SocketChannel) readyKey.channel();

                    if (channel.socket().isClosed()) {
                        LOG.warn("The remote has been closed, so I kill it");
                        stopClient(readyKey);
                        continue;
                    }

                    if (readyKey.isValid() && readyKey.isReadable()) {
                        this.getDataFromMiddle(readyKey, channel);
                    }

                    /*if (readyKey.isValid() && readyKey.isAcceptable()) {
                        System.out.println("client is isAcceptable");
                    } else if (readyKey.isValid() && readyKey.isWritable()) {
                    } else if (readyKey.isValid() && readyKey.isReadable()) {
                        this.messageForwardFromMiddle(readyKey, channel);;
                    } else if (!readyKey.isValid()) {
                        throw new Exception("CONNECT_IS_NOT_VALID");
                    }*/

                }
            }
        } catch (Exception e) {
            LOG.error("", e);
        } finally {

        }
    }

    //up link
    public void startClientProxyLoop() {
        try {
            while (true) {
                int keyCount = this.localSelector.select(100);
                if (keyCount == 0) {
                    continue;
                }
                Iterator<SelectionKey> selecionKeys = this.localSelector.selectedKeys().iterator();
                while (selecionKeys.hasNext()) {
                    SelectionKey readyKey = selecionKeys.next();
                    selecionKeys.remove();
                    SocketChannel channel = (SocketChannel) readyKey.channel();

                    if (readyKey.isValid() && readyKey.isAcceptable()) {
                    } else if (readyKey.isValid() && readyKey.isWritable()) {
                    } else if (readyKey.isValid() && readyKey.isReadable()) {
//                        TimeUnit.MILLISECONDS.sleep(1);
                        this.getDataFromLocal(readyKey, channel, this.remoteChannel);
                    } else if (!readyKey.isValid()) {
                        throw new Exception("CONNECT_IS_NOT_VALID");

                    }

                }
            }
        } catch (Exception e) {
            LOG.error("", e);
        } finally {

        }
    }

    public static void main(String[] args) {
        Options options = new Options();
        options.addRequiredOption("s", "server", true, "Remote server ip address and port. Example: 192.168.1.2:9000");
        options.addRequiredOption("c", "client", true, "Local service address. Example: 192.168.1.2:22/22(ip can be empty)");
        options.addRequiredOption("p", "proxy-port", true, "Proxy port run on server");
        options.addRequiredOption("n", "name", true, "The registered name");
        options.addRequiredOption("k", "key", true, "The secret key for login");
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("show this help message and exit program")
                .build());

        options.addOption("a", "security", false, "Outer link need to auth, then to connect");

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            formatter.printHelp("ddns-client", options, false);
            return;
        }
        if (cmd.hasOption('h') || cmd.hasOption("--help")) {
            formatter.printHelp("ddns-client", options, false);
            return;
        }

        String serverCfg = cmd.getOptionValue("s");
        String clientCfg = cmd.getOptionValue("c");
        String proxyPortCfg = cmd.getOptionValue("p");
        String nameCfg = cmd.getOptionValue("n");
        String keyCfg = cmd.getOptionValue("k");

        if(!Pattern.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+$", serverCfg)){
            System.err.println("Invalid server format.(Example: 1.1.11.2:80)");
            System.err.println("");
            formatter.printHelp("ddns-client", options, false);
            return;
        }

        if(!Pattern.matches("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+)|(\\d+)$", clientCfg)){
            System.err.println("Invalid client format.(Example: 1.1.11.2:80 or 80)");
            System.err.println("");
            formatter.printHelp("ddns-client", options, true);
            return;
        }

        if(!Pattern.matches("^\\d+$", proxyPortCfg)){
            System.err.println("Invalid proxy port format.(Example: 80)");
            System.err.println("");
            formatter.printHelp("ddns-client", options, true);
            return;
        }
        Integer proxyPortInt = Integer.parseInt(proxyPortCfg);


        String[] serverOpts = serverCfg.split(":");
        String remoteIp = serverOpts[0];
        String remotePort = serverOpts[1];
        Integer remotePortInt = Integer.parseInt(remotePort);


        String[] localOpts = clientCfg.split(":");
        String localIp, localPort;
        if(localOpts.length == 1){
            localIp = "127.0.0.1";
            localPort = localOpts[0];
        } else {
            localIp = localOpts[0];
            localPort = localOpts[1];
        }
        Integer localPortInt = Integer.parseInt(localPort);

        Client cl = new Client(remoteIp, remotePortInt, proxyPortInt, localIp, localPortInt);
        cl.setName(nameCfg);
        cl.setSecretKey(keyCfg);
        if(cmd.hasOption("a")){
            LOG.info("Client has set security. The work port need to validate ip.");
            cl.setSecurity(RegisterProtocol.SECURITY);
        }
        new Thread(cl).start();


//        Client cl = new Client("47.98.136.177", 9000, 2323, "127.0.0.1", 80);
//        Client cl = new Client("192.168.122.45", 9000, 2323, "127.0.0.1", 80);
//        Client cl = new Client("127.0.0.1", 9000, 7777, "127.0.0.1", 7788);
//        Client cl = new Client("127.0.0.1", 9000, 9001, "127.0.0.1", 5201);
//        Client cl = new Client("192.168.122.45", 9000, 9001, "127.0.0.1", 8000);
//        cl.setName("wls-ssh");
//        cl.setSecretKey("1234567890123456");
//        cl.setSecurity(RegisterProtocol.SECURITY);
//        new Thread(cl).start();

    }

}
