package org.wls.ddns.backup.socket;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Created by wls on 2019/3/27.
 */
public class SocketTest {
    public static void main(String[] args) {
//        Map<Integer, SelectionKey> keyConvertMap = new ConcurrentHashMap<>();
//        keyConvertMap.remove(null);
//        Integer a = 11111;
//        char b;
//        b = (char)a.intValue();
//        System.out.println(b);

//        System.out.println(c);



//        ByteBuffer b = ByteBuffer.allocate(16);
//
//        ByteBuffer c = ByteBuffer.allocate(12);
//
//        c.putInt(12);
//        c.putInt(12);
//        c.putInt(12);
//        c.flip();
//
//        System.out.println(b.remaining());
//        b.putInt(1000);
////        b.put(c);
//        b.flip();
//        System.out.println(b.remaining());
//        System.out.println(b.hasRemaining());
//        b.getInt();
//        System.out.println(b.remaining());
//        System.out.println(b.hasRemaining());
//
//        System.out.println();

//        short a = Short.MAX_VALUE;
//        a++;
//        a++;
//        a++;
//        short a1 = (short) (a & Short.MAX_VALUE);
//        System.out.println(a1);

//        short before = -8;
//        short after = 200;
//        int protocol = 0;
//
//        protocol = before<<16;
//        protocol = protocol| after;
//        System.out.println(protocol);
//
//        System.out.println(protocol >> 16);
//        System.out.println(protocol & 0x7FFF);

        IntStream.range(0, 10).forEach(i-> System.out.println(i));

        ByteBuffer b = ByteBuffer.allocate(16);
        b.putInt(1);
        b.putInt(1);
        b.putInt(1);
        b.putInt(1);
        b.flip();

//        b.getInt();
//        ByteBuffer c = ByteBuffer.allocate(12);
        byte[] bytes =new byte[12];
        b.get(bytes, 0, 12);
        System.out.println(b.remaining());

        System.out.println(bytes[1]);

//
//
//        byte[] bb = b.array();
//        for(int  i= 0 ; i< bb.length ; i++){
//            System.out.println(bb[i]);
//        }
//        Arrays.asList().stream().forEach(i->{
//            System.out.println(i);
//        });

            /*try {
                Socket s = new Socket("127.0.0.1", 9000);
                OutputStream out = s.getOutputStream();
                BufferedOutputStream out1 = new BufferedOutputStream(out,1024);
                System.out.println("---- 1");
//                out1.
//                out1.writeUTF("Hello from " + s.getLocalSocketAddress());
                System.out.println("---- 2");
//                byte[] b = {'a','b'};
//                out.write(b);
                InputStream inFromServer = s.getInputStream();
                System.out.println("---- 3");
                DataInputStream in = new DataInputStream(inFromServer);
                System.out.println("---- 4");
                System.out.println("服务器响应： " + in.readUTF());


                AtomicInteger i= new AtomicInteger();

            } catch (IOException e) {
                e.printStackTrace();
            }*/

//        Integer h = Integer.MAX_VALUE + 1;
//        System.out.println(h);
//
//        Short s = 102;
//        s++;
//        System.out.println(s & 0x7FFF);


//        Integer a = Integer.MAX_VALUE;
//        System.out.println(Integer.toBinaryString(a));
//        a++;
//        System.out.println(Integer.toBinaryString(a & 0x7fffffff));
//        System.out.println(a & 0x7fffffff);
//        System.out.println(Byte.toString(a));


//        System.out.println(a);
        }
}
