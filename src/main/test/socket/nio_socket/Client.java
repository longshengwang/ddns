package org.wls.ddns.backup.socket.nio_socket;

import java.io.IOException;
import java.net.BindException;
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
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * Created by wls on 2019/3/28.
 */
public class Client implements Runnable{
    public static Logger log = Logger.getLogger(Client.class.toString());

    private String remoteIp;
    private Integer remotePort;
    private Integer proxyPort;
    private String localIp;
    private Integer localPort;
    private Selector localSelector;
    private Selector remoteSelector;
//    private SocketChannel clientChannel= null;
    private SocketChannel remoteChannel= null;
    private Map<Integer, SelectionKey> indexProxyMap = new ConcurrentHashMap<>();
    private Map<SelectionKey, Integer> keyProxyMap = new ConcurrentHashMap<>();
    private Map<SelectionKey, ByteBuffer> keyByteBufferMap = new ConcurrentHashMap<>();
    private Map<SelectionKey, Metadata>  dataInfoMap= new HashMap<>();
    private Lock connectionLock = new ReentrantLock();
    private Lock clientStopLock = new ReentrantLock();

    public  static  Long testCount = 0L;

    public Client(String remoteIp, Integer remotePort, Integer proxyPort, String localIp, Integer localPort){
        this.localPort = localPort;
        this.remotePort = remotePort;
        this.remoteIp = remoteIp;
        this.localIp = localIp;
        this.proxyPort = proxyPort;
    }

    private static void registerSocketChannel(SocketChannel socketChannel, Selector selector, Integer bufferSize) throws IOException {
        System.out.println("registerSocketChannel is " + socketChannel.socket().getRemoteSocketAddress());
        socketChannel.configureBlocking(false);
        //socket通道可以且只可以注册三种事件SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT
        socketChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(bufferSize));
    }


    private boolean initLocalConnect(){
        try {
            this.localSelector = Selector.open();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }

//        if(clientSocket!= null && clientSocket.isConnected()){
//            try {
//                clientSocket.close();
//                this.localSelector.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
        return false;
    }

    private void addLocalConnect(Integer indexId) throws IOException {
        log.info("[新增到内部服务的连接][开始] indexID:" + indexId);

        ByteBuffer middleBuffer = ByteBuffer.allocate(SocketTool.PROTOCOL_BUFFER_SIZE);
        SocketChannel clientChannel = SocketChannel.open();
        Socket clientSocket = clientChannel.socket();
        clientSocket.connect(new InetSocketAddress(this.localIp, this.localPort));
        registerSocketChannel(clientSocket.getChannel(), this.localSelector, SocketTool.BUFFER_SIZE);

        SelectionKey clientKey = clientSocket.getChannel().keyFor(this.localSelector);
        indexProxyMap.put(indexId, clientKey);
        keyProxyMap.put(clientKey, indexId);
        keyByteBufferMap.put(clientKey, middleBuffer);
        log.info("[新增到内部服务的连接][结束] indexID:" + indexId);
    }

    private boolean initRemoteConnect(){
        Socket remoteSocket = null;
        try {
            this.remoteSelector = Selector.open();
            remoteChannel = SocketChannel.open();
            remoteSocket = remoteChannel.socket();
            remoteSocket.connect(new InetSocketAddress(this.remoteIp, this.remotePort));
            //            registerSocketChannel(remoteSocket.getChannel(), this.remoteSelector);
            //            SelectionKey clientKey = remoteSocket.getChannel().keyFor(remoteSelector);
            registerSocketChannel(remoteSocket.getChannel(), this.remoteSelector, SocketTool.PROTOCOL_BUFFER_SIZE); //暂时都用一个selector
            SelectionKey middleKey = remoteSocket.getChannel().keyFor(remoteSelector);


            // 这里需要通知server端需要用代理端口
            ByteBuffer b = ByteBuffer.allocate(SocketTool.PROTOCOL_BUFFER_SIZE);
            b.putInt(proxyPort.intValue());
            b.flip();
            remoteChannel.write(b);

            while(b.hasRemaining()){
                remoteChannel.write(b);
            }

            // 这里需要等待server 告诉client 代理端口是否成功开启
            ByteBuffer o = (ByteBuffer)middleKey.attachment();
            int len =0;
            // 0: ok
            // 1: error
            while((len = remoteChannel.read(o)) == 0){
                Thread.sleep(100);
            }
            o.flip();
            int status = o.getInt(0);
            o.clear();
            if(status == 0){
                log.info("远程端口正确开启");
                return true;
            }
//            return true;
        } catch (IOException e) {
            e.printStackTrace();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
//        if(remoteSocket!= null && remoteSocket.isConnected()){
//            System.out.println("remote socket 有问题");
//            try {
//                remoteSocket.close();
//                this.remoteSelector.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
        return false;
    }

    //local connection tell me to close
    private void stopClient(SelectionKey readyKey) throws IOException {


        Integer indexId = this.keyProxyMap.get(readyKey);
//        clientStopLock.lock();
        System.out.println("[警告]stopClient(SelectionKey readyKey): indexid: " + indexId + " >>>>>>>>");
        try{
            ByteBuffer bf = this.keyByteBufferMap.get(readyKey);
            if(bf == null){
                System.out.println("bytebuffer已经被清除了");
                return;
            }
            sendCloseFlag2Middle(indexId);
            readyKey.channel().close();
            System.out.println("[警告]stopClient(SelectionKey readyKey) : indexid: " + indexId + " <<<<<<<<");
        } catch (IOException e){
            throw e;
        } finally {
            this.keyByteBufferMap.remove(readyKey);
//            this.dataInfoMap.remove(readyKey);
            this.keyProxyMap.remove(indexId);
            this.indexProxyMap.remove(indexId);
//            clientStopLock.unlock();
        }







    }

    private void clearAll(){

    }

    //middle connection tell me to close
    private void stopClient(Integer indexId) throws IOException {
//        clientStopLock.lock();
        try{
            System.out.println("[警告]client stop client indexID:" + indexId);
            SelectionKey key = this.indexProxyMap.get(indexId);

            if(key != null){
                if(key.channel() !=null){
                    key.channel().close();
                }
                this.keyByteBufferMap.remove(key);
//            this.dataInfoMap.remove(key);
            }

            this.keyProxyMap.remove(indexId);
            this.indexProxyMap.remove(indexId);
        } catch (Exception e){
            throw e;
        } finally {
//            clientStopLock.unlock();
        }

    }

    private void sendCloseFlag2Middle(Integer index) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int id = index.intValue();

        short closeId = (short)-id;
//        buffer.putShort(closeId);
        buffer.putInt(SocketTool.encodeProtocol((short)0, closeId));
        buffer.flip();

        this.remoteChannel.write(buffer);
        while(buffer.hasRemaining()){
            this.remoteChannel.write(buffer);
        }
//        buffer.clear();
    }

    //上行数据
    private void getDataFromLocal(SelectionKey localKey, SocketChannel readChannel, SocketChannel writeChannel) {
        ByteBuffer byteBuffer = (ByteBuffer) localKey.attachment();
        ByteBuffer middleBuffer = keyByteBufferMap.get(localKey);
        int count = 0;
        while(middleBuffer == null && count < 10){
            System.out.println("[上行数据]临时缓存还没有建立，睡上100ms");
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            middleBuffer = keyByteBufferMap.get(localKey);
            count++;
        }
        try{
            int len = 0;
            if((len = readChannel.read(byteBuffer)) > 0){
                byteBuffer.flip();

//                System.out.println("[上行数据]middleBuffer:" +  middleBuffer);
//                System.out.println("[上行数据]keyProxyMap.get(readyKey):" +  keyProxyMap.get(readyKey));
                middleBuffer.putInt(SocketTool.encodeProtocol((short)len, (short)keyProxyMap.get(localKey).intValue()));
//                System.out.println("[上行数据]------------------------------------");
//                System.out.println("[上行数据]id:" + keyProxyMap.get(readyKey));
//                System.out.println("[上行数据]len:" + len);
                middleBuffer.put(byteBuffer);
                middleBuffer.flip();
//                System.out.println("[上行数据]发给middle的len:" + middleBuffer.remaining());
                writeChannel.write(middleBuffer);
                while(middleBuffer.hasRemaining()){
                    writeChannel.write(middleBuffer);
                }
                byteBuffer.clear();
                middleBuffer.clear();
            }
            if(len == -1){
//            readyKey.channel().close();
                System.out.println("[上行数据]client 数据已经全部返回 indexid====》》》");
                stopClient(localKey);
            }
        } catch (IOException e){
            e.printStackTrace();
            System.out.println("[上行数据]client 端主动断开了连接，抛出异常");
            try{
                stopClient(localKey);
            } catch (IOException e1){
                System.out.println("[上行数据]抛出异常时尝试关闭连接失败");
            }

        }

    }

    void loopParseMiddleData(SelectionKey serverKey, ByteBuffer byteBuffer) throws IOException{
        log.info("key:" + serverKey + ".... bytebuffer remain:" + byteBuffer.remaining());
//        ByteBuffer byteBuffer = (ByteBuffer) readyKey.attachment();
        Metadata dataInfo = dataInfoMap.get(serverKey);
        if(byteBuffer.remaining() == 0){
            System.out.println("[下行数据]remain 是空");
//            byteBuffer.clear();
            return;
        }
        if(dataInfo.isNull()){
            int protocolHeader = byteBuffer.getInt();
            int indexId = protocolHeader >> Short.SIZE;
            short dataSize = (short) (protocolHeader & Short.MAX_VALUE);
//            System.out.println("[ loopParseData ] 索引ID：" + indexId + "    dataSize:" + dataSize);
            if(indexId < 0){
                System.out.println("[下行数据] [关闭消息]对端通知关闭了 indexId:" + indexId);
                int realIndexId = -indexId;
                stopClient(realIndexId);
                loopParseMiddleData(serverKey, byteBuffer);
                return;
            }

            if(indexProxyMap.get(indexId) == null){
                try{
                    addLocalConnect(indexId);
                } catch (IOException e){
                    System.out.println("[错误]连接到内部服务失败(内部端口未启动)");
                    sendCloseFlag2Middle(indexId);
                    System.out.println("remain1:" + byteBuffer.remaining());
                    IntStream.range(0, dataSize).forEach(i->byteBuffer.get());

                    System.out.println("remain2:" + byteBuffer.remaining());
//                    if(byteBuffer.remaining() > 0){
//                        System.out.println("");
                    loopParseMiddleData(serverKey, byteBuffer);
//                    } else {
//                        byteBuffer.clear();
//                    }
                    return;
                }

            }
//            System.out.println("[Down数据]新的一段数据， ID："+ indexId + " , size: "+ dataSize);
//            System.out.println("[Down数据]byteBuffer size：" + byteBuffer.remaining());
            dataInfo.set((int)dataSize, indexId);
        }


        SelectionKey key = indexProxyMap.get(dataInfo.getIndexId());
        if(key == null){
            //表示连接内部的连接已经断开，那么需要清除dataInfo，还有当前的缓存也需要clear
            System.out.println("[下行数据]key是空的，当然需要去关闭了");
            stopClient(key);
            dataInfo.reset();
//            byteBuffer.clear();
            //清除本来要发往内部的数据
            byte[] remainBytes = new byte[dataInfo.getRemainingLength()];
            byteBuffer.get(remainBytes);
            loopParseMiddleData(serverKey, byteBuffer);
            return;
        }
        SocketChannel writeChannel = (SocketChannel)key.channel();
        if(dataInfo.getRemainingLength() == byteBuffer.remaining()){
            try{
                dataInfo.decrease(byteBuffer.remaining());

                writeChannel.write(byteBuffer);
                if(byteBuffer.hasRemaining()){
                    writeChannel.write(byteBuffer);
                }
            }catch (IOException e){
                System.out.println("[Down数据][警告]=== 我抓住了这个异常 (1  -  1) >>>>");
                e.printStackTrace();
                System.out.println("[Down数据][警告]=== 我抓住了这个异常 <<<<<");
                stopClient(key);
                dataInfo.reset();
            } finally {
                byteBuffer.clear();
            }

        } else if(dataInfo.getRemainingLength() > byteBuffer.remaining()){
            try{
                dataInfo.decrease(byteBuffer.remaining());

                writeChannel.write(byteBuffer);
                if(byteBuffer.hasRemaining()){
                    writeChannel.write(byteBuffer);
                }
            } catch (IOException e){
                e.printStackTrace();
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
            try{
                writeChannel.write(remainBuffer);
                if(remainBuffer.hasRemaining()){
                    writeChannel.write(remainBuffer);
                }
                dataInfo.reset();
            } catch (IOException e){
                e.printStackTrace();
//                stopClient(key);
                dataInfo.reset();
            } finally {
                loopParseMiddleData(serverKey, byteBuffer);
            }
        }


    }


    //下行数据
    private void getDataFromMiddle(SelectionKey serverKey, SocketChannel readChannel) throws IOException {
        log.info("进入到解析的流程了");
        if(dataInfoMap.get(serverKey) == null){
            dataInfoMap.put(serverKey, new Metadata());
        }
        ByteBuffer byteBuffer = (ByteBuffer) serverKey.attachment();
        if(byteBuffer.remaining() > 0){
            log.severe("有未清除的数据，会导致整个流程发生错误");

        }

        int len = 0;
        if((len = readChannel.read(byteBuffer)) > 0){
//            testCount += len;
            byteBuffer.flip();
            System.out.println("[下行数据]byteBuffer数据长度：" + len);
            loopParseMiddleData(serverKey, byteBuffer);
        }

//        System.out.println("总共流量是：" + testCount);
//        System.out.println("go on");
        if(len == -1){
            System.out.println("[Down数据]middle 通道已经断开了");
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
        //middle 通道有问题，已经断开连接了
        if(len == -1){
            System.out.println("middle 通道已经断开了");
            readyKey.channel().close();
            clearAll();
        }*/
    }

    @Override
    public void run() {
        initLocalConnect();
        if(!initRemoteConnect()){
            System.out.println("[ERROR]初始化失败，服务器端返回错误");
            return;
        }

        new Thread(){
            public void run(){
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

                //循环读取middle的内容
                while (selecionKeys.hasNext()) {
                    SelectionKey readyKey = selecionKeys.next();
                    selecionKeys.remove();
                    SocketChannel channel = (SocketChannel)readyKey.channel();

                    if(channel.socket().isClosed()){
                        System.out.println("对端已经关闭了，所以我这边也关掉他");
                        stopClient(readyKey);
                        continue;
                    }

                    if (readyKey.isValid() && readyKey.isReadable()) {
                        this.getDataFromMiddle(readyKey, channel);;
                    }

                    /*if (readyKey.isValid() && readyKey.isAcceptable()) {
                        //这里并不会走到，因为remoteSelector只是一个client
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
            e.printStackTrace();
        } finally {

        }
    }

    //up link
    public void startClientProxyLoop(){
        try {
            while (true) {
                int keyCount = this.localSelector.select(100);
                if (keyCount == 0) {
                    continue;
                }
//                System.out.println("client一有数据返回了");
                Iterator<SelectionKey> selecionKeys = this.localSelector.selectedKeys().iterator();
                while (selecionKeys.hasNext()) {
                    SelectionKey readyKey = selecionKeys.next();
                    selecionKeys.remove();
                    SocketChannel channel = (SocketChannel)readyKey.channel();

                    if (readyKey.isValid() && readyKey.isAcceptable()) {
                    } else if (readyKey.isValid() && readyKey.isWritable()) {
                    } else if (readyKey.isValid() && readyKey.isReadable()) {

                        this.getDataFromLocal(readyKey, channel, this.remoteChannel);;

                    } else if (!readyKey.isValid()) {
                        throw new Exception("CONNECT_IS_NOT_VALID");

                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
    }

    public static void main(String[] args) {
        Client cl = new Client("127.0.0.1", 9000, 9003, "127.0.0.1", 99);
        new Thread(cl).start();
//        ByteBuffer b = ByteBuffer.allocate(4096);
//        Integer x = 121212;
//        System.out.println(x.byteValue());
//        b.putInt(x);
//        System.out.println(b.getInt(0));
    }

}
