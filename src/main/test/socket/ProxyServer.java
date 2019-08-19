package org.wls.ddns.backup.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

/**
 * Created by wls on 2019/7/26.
 */
public class ProxyServer {

    private ServerSocketChannel serverChannel;
    private ServerSocket serverSocket;
    private Selector selector;
    private Integer port;
    public ProxyServer(Integer port, Selector selector){
        this.selector = selector;
        this.port = port;

    }

    public void init(){
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverSocket = serverChannel.socket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(this.port));
            serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SelectionKey getSelectionKey(){
        return serverSocket.getChannel().keyFor(this.selector);
    }

    public static void main(String[] args) throws InterruptedException {
        {
            ProxyServer p = new ProxyServer(1090, null);
            p.init();
            Thread.sleep(10000);

        }
        System.out.println("开始回收");
        System.gc();
        while(true){
            Thread.sleep(10000);
        }
    }
}
