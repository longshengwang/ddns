package org.wls.ddns;

import com.sun.jmx.snmp.SnmpUnsignedInt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Created by wls on 2019/8/13.
 */
public class T {
    public static void main(String[] args) throws IOException, InterruptedException {


        IntStream.range(0, 10).forEach(i-> System.out.println(i));

//        System.out.println("11");
//        Selector localSelector = Selector.open();
//        int a = 12;
//
//        new Thread(new Runnable() {
//            @Override
//            public void run(){
//                while (true) {
//                    int keyCount = 0;
//                    try {
//                        keyCount = localSelector.select(100);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                    if (keyCount == 0) {
//                        System.out.println("keycount = 0");
//                        continue;
//                    } else {
//                        System.out.println("keycount >>>>> 0");
//                    }
//
//                }
//            }
//        }).start();
//
//
//        SocketChannel clientChannel = null;
//        try {
//            clientChannel = SocketChannel.open();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        System.out.println("12");
//        Socket clientSocket = clientChannel.socket();
//        clientSocket.connect(new InetSocketAddress("localhost", 8080));
//        System.out.println("13");
//        clientSocket.getChannel().configureBlocking(false);
//        //socket通道可以且只可以注册三种事件SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT
//        clientSocket.getChannel().register(localSelector, SelectionKey.OP_READ, ByteBuffer.allocate(4096));
//        System.out.println("14");
//        SelectionKey clientKey = clientSocket.getChannel().keyFor(localSelector);
//        System.out.println(clientKey);
//        System.out.println("15");
//        TimeUnit.SECONDS.sleep(100);
    }
}
