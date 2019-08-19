package org.wls.ddns.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by wls on 2019/8/8.
 */
public class ConnModel {
    private static final Logger LOG = LogManager.getLogger(ConnModel.class);
    //注意这里不能用基础类型，需要用基础类型包装类。
    private Integer proxyPort;
    private Integer localPort;
    private String localName;
    private Integer security;
    private Long byteSend;
    private Long byteReceive;
    private List<String> trustIps;

    public ConnModel(RegisterProtocol protocol){
        this.proxyPort = protocol.getProxyPort();
        this.localPort = protocol.getLocalPort();
        this.localName = protocol.getName();
        this.security = protocol.getSecurity();
        this.byteSend = 0L;
        this.byteReceive = 0L;
        this.trustIps = new ArrayList<>();
    }

    public void addByteSend(Integer byteSendSize){
        this.byteSend += byteSendSize;
    }

    public void addByteReceive(Integer byteRecSize){
        this.byteReceive += byteRecSize;

    }

    public ConnModel(){
//        this.proxyPort = 10;
//        this.localPort = 20;
//        this.localName = "家里";
//        this.byteReceive = 1212L;
//        this.byteSend = 11212L;
    }


    public Integer getSecurity() {
        return security;
    }

    public void setSecurity(Integer security) {
        this.security = security;
    }

    public List<String> getTrustIps() {
        return trustIps;
    }

    public void setTrustIps(List<String> trustIps) {
        this.trustIps = trustIps;
    }


    public String getLocalName() {
        return localName;
    }

    public Map<String, String> toMap(){
        Map<String,String> map = new HashMap<>();
        Arrays.stream(ConnModel.class.getDeclaredFields()).forEach(f->{

            try {
//                System.out.println(f.get(this));
                map.put(f.getName(), String.valueOf(f.get(this)));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        });
        return map;
    }


    public String toString(){
        return "ConnModel:\n" +
                "{\n" +
                "    name:" + this.localName + "\n" +
                "    proxyPort:" + this.proxyPort + "\n" +
                "    localPort:" + this.localPort + "\n" +
                "    byteSend:" + this.byteSend + "\n" +
                "    byteReceive:" + this.byteReceive + "\n" +
                "}\n";
    }

}

