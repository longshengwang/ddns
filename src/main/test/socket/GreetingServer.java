package org.wls.ddns.backup.socket;

import java.net.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class GreetingServer {
    public ServerSocket serverSocket;
//    public Socket clientSocket;
    public Socket server;

    public GreetingServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
//        serverSocket.setSoTimeout(10000);
//        serverSocket.setSoTimeout(1000);

//        clientSocket = new Socket("127.0.0.1", 9000);
        System.out.println("======1 ");
//        OutputStream out = clientSocket.getOutputStream();
        System.out.println("======2 ");
//        DataOutputStream out1 = new DataOutputStream(out);
        System.out.println("======3 ");
//        out1.writeUTF("Hello from " + clientSocket.getLocalSocketAddress());

//        InputStream inFromServer = clientSocket.getInputStream();
//        DataInputStream in = new DataInputStream(inFromServer);
//        System.out.println("服务器响应： " + in.readUTF());
    }

    public void run() throws IOException {
        System.out.println("=======");
//        this.server = serverSocket.accept();
        while (true) {
            try {
                System.out.println("[main] run 1");
                byte[] b = new byte[1024];
                System.out.println("等待远程连接，端口号为：" + serverSocket.getLocalPort() + "...");
                Socket _server = serverSocket.accept();
                System.out.println("远程连接来了 ……………………………………………………" + _server.getRemoteSocketAddress());
//                new Thread(() -> {
                    try{
                        Socket server = _server;
                        Socket clientSocket = new Socket("127.0.0.1", 3000);
                        System.out.println("oooooh shit: " +clientSocket.getChannel());

                        Thread outThread  = new Thread(new DataTransfor(server, clientSocket, "[data in <<]"));
                        outThread.start();

                        Thread inThread  = new Thread(new DataTransfor(clientSocket, server, "[data out >>]"));
                        inThread.start();

                        System.out.println("_server.isClosed(): " + _server.isClosed());


//                        IntStream.range(0, 100).forEach(i->{
//                            System.out.println(i);
//                            if(outThread.isAlive() && inThread.isAlive()){
//                                System.out.println("xiancheng is alive");
//                                System.out.println(_server.isClosed());
//                                System.out.println(_server.isConnected());
//                                System.out.println(_server.isConnected());
//                                try {
//                                    Thread.sleep(1000);
//                                } catch (InterruptedException e) {
//                                    e.printStackTrace();
//                                }
//                            } else {
//                                System.out.println("xiancheng is not === >alive");
//                            }
//                        });
                        try{
                            outThread.join();
                        } catch(Exception e){
                            e.printStackTrace();
                        }

                        try{
                            inThread.join();
                        } catch(Exception e){
                            e.printStackTrace();
                        }

//                        server.close();
                        clientSocket.close();
                        _server.close();
//                        Thread.sleep(1000);
                        System.out.println("[main] run 2");
                    } catch (SocketTimeoutException s) {
                        System.out.println("[main Exception] Socket timed out!");
                    } catch (IOException e) {
                        System.out.println("[main Exception] Socket IOException out!");
                        e.printStackTrace();
                    } catch (Exception e) {
                        System.out.println("[main Exception] Exception timed out!");
                        e.printStackTrace();
                    }
//                }).start();
            } catch (SocketTimeoutException s) {
                System.out.println("[main ExceptionOUT] Socket timed out!");
                break;
            } catch (IOException e) {
                System.out.println("[main ExceptionOUT] Socket IOException out!");
                e.printStackTrace();
                break;
            } catch (Exception e) {
                System.out.println("[main ExceptionOUT] Exception timed out!");
                e.printStackTrace();
                break;
            }


        }


    }


//    class T extends Thread{
//
//        T(){
//
//        }
//
//        @Override
//        public void run() {
//            super.run();
//        }
//    }


    public static void main(String[] args) {
        try {

            Socket clientSocket = new Socket("127.0.0.1", 4200);
//            GreetingServer t = new GreetingServer(9000);
//            t.run();
        } catch (IOException e) {
            e.printStackTrace();

        }
    }
}