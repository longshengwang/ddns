package org.wls.ddns.http;

import org.wls.ddns.model.ConnModel;

import java.nio.channels.SelectionKey;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by wls on 2019/8/8.
 */
public class ProxyConfig {
    public String id;
    Map<SelectionKey, ConnModel> connectionStatisticMap;


    public void setStatisticMap( Map<SelectionKey, ConnModel> map){
        this.connectionStatisticMap = map;
    }

    public List<Map<String, String>> getAllConnectionInfo(){
        List<ConnModel> connList = new ArrayList<>(connectionStatisticMap.values());
        return connList.stream().map(c->c.toMap()).collect(Collectors.toList());
    }

    public void addTrustIps(String name, String ip){
        connectionStatisticMap.values().stream().forEach(f->{
                if(name.equals(f.getLocalName())){
                    f.getTrustIps().add(ip);
                }
            }
        );

    }

    public static void main(String[] args) {
//        String a = "我的";
//        System.out.println(a.length());
//        System.out.println(a.getBytes().length);

//        int n = 10;
//        char[] chars = new char[n];
//        Arrays.fill(chars, 'c');
//        String result = new String(chars);


//        System.out.println(result);
    }
//        List<ConnModel> list = new ArrayList<>();
//        list.add(new ConnModel());
//        list.add(new ConnModel());
//        list.add(new ConnModel());
//
//
//        /*list.stream().map(p->{
//            Map connMap = new HashMap<String, String>();
////            connMap.put("localport");
////            return;
//        })*/
//    }
}
