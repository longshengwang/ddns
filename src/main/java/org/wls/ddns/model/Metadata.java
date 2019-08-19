package org.wls.ddns.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by wls on 2019/8/2.
 * 主要是用来记录每个连接的数据长度，这样才可以区分不同连接的包。
 */
public class Metadata {
    private Integer remainingLength;
    private Integer indexId;
    // proxy port 字段只在server端会记录，主要是流量统计用到，流量统计只在服务器上做
    private Integer proxyPort;
    private ConnModel connModel = null;

    private List<Byte> protocolBytesNotComplete = new ArrayList<>();

    public Metadata(Integer proxyPort){
        this.proxyPort = proxyPort;
    }

    public void setConnectionModel(ConnModel connModel){
        this.connModel = connModel;
    }
    public Metadata(){
//        this.proxyPort = proxyPort;
    }

    public Integer getProxyPort(){
        return this.proxyPort;
    }

    public void set(Integer allLength, Integer indexId){
        if(isNull()){
            this.remainingLength = allLength;
            this.indexId = indexId;
        } else {
            System.out.println("AAAAAA 出现错误啦。。。 有数据没发完 indexID: " + this.indexId + "   remaining length: "+ this.remainingLength);
        }
    }

    public Integer decrease(Integer size){
        remainingLength -= size;
        if(connModel != null){
            connModel.addByteReceive(size);
        }
        if(remainingLength == 0){
            reset();
        }
        return remainingLength;
    }

    public Integer getIndexId(){
        return indexId;
    }


    public Integer getRemainingLength(){
        return remainingLength;
    }

    public void reset(){
        remainingLength = null;
        indexId = null;
    }

    public boolean isNull(){
        if(remainingLength == null && indexId == null){
            return true;
        }
        return false;
    }


    public void clearNotCompleteProtocolIndex(){
        protocolBytesNotComplete.clear();
    }

    public boolean hasNotCompleteProtocolIndex(){
        return protocolBytesNotComplete.size() > 0;
    }

    public Integer getNotCompleteProtocolSize(){
        return protocolBytesNotComplete.size();
    }

    public void putByte2NotCompleteProtocol(Byte b){
        protocolBytesNotComplete.add(b);
    }

    public Integer getIndexByNotCompleteList(){
        if(protocolBytesNotComplete.size() < 4){
            return null;
        }
        int b0 = protocolBytesNotComplete.get(0) & 0xFF;
        int b1 = protocolBytesNotComplete.get(1) & 0xFF;
        int b2 = protocolBytesNotComplete.get(2) & 0xFF;
        int b3 = protocolBytesNotComplete.get(3)& 0xFF;

        clearNotCompleteProtocolIndex();

        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    public static void main(String[] args) {
//        Integer i = 1000000;
//        byte[] targets = new byte[4];
//        targets[3] = (byte) (i & 0xFF);
//        targets[2] = (byte) (i >> 8 & 0xFF);
//        targets[1] = (byte) (i >> 16 & 0xFF);
//        targets[0] = (byte) (i >> 24 & 0xFF);
//
//        System.out.println(targets[0]);
//        System.out.println(targets[1]);
//        System.out.println(targets[2]);
//        System.out.println(targets[3]);
//
//        System.out.println(Arrays.asList(targets).size());





    }


}
