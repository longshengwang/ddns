package org.wls.ddns.backup.socket;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Created by wls on 2019/3/28.
 */



public class DataTransfor implements Runnable {

    private Socket in;
    private Socket out;
    private String tag ;
    public final static int BUFFER_SIZE = 1024;

    public DataTransfor(Socket in, Socket out, String tag) {
        this.in = in;
        this.out = out;
        this.tag = tag;
    }

    @Override
    public void run() {
        long threadId = Thread.currentThread().getId();
//        System.out.println(threadId + '-' + this.tag + " ==> Get Start()");
        byte[] buffer = new byte[2048];
        BufferedInputStream inStream = null;
        BufferedOutputStream outStream = null;
        InputStream inputStream = null;
        OutputStream outputStream = null;
//        while (true) {
            try {
                inputStream = this.in.getInputStream();
                outputStream = this.out.getOutputStream();
//                inStream = new BufferedInputStream(this.in.getInputStream(), BUFFER_SIZE);
//                outStream = new BufferedOutputStream(this.out.getOutputStream(), BUFFER_SIZE);

                int bufferSize;

                while ((bufferSize = inputStream.read(buffer)) > 0) {
                    System.out.println(this.tag + " loop bufferSize: " + bufferSize);
                    outputStream.write(buffer, 0, bufferSize);
                    outputStream.flush();
                }
            } catch (SocketTimeoutException s) {
                System.out.println(threadId + '-' +this.tag +" ==>Socket timed out!");
            } catch (IOException e) {
                System.out.println(threadId + '-' +this.tag + " ==>Socket IO out!");
                e.printStackTrace();
            } catch (Exception e){
                System.out.println(threadId + '-' +this.tag + " ==>Exception IO out!");
                e.printStackTrace();
            } finally {
                if(inputStream != null){
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

//                if(outputStream != null){
//                    try {
//                        outputStream.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
                System.out.println(threadId + '-' + this.tag + " ==> GO to finally");

            }
//        }
    }
}
