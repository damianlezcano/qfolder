/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.q3s.p2p.client;

import org.q3s.p2p.model.User;
import org.q3s.p2p.model.Workspace;

/**
 *
 * @author damianlezcano
 */
public class Config {
 
    public static final int FILE_PART_SIZE_IN_KB = 100;
    
	public static String URL_GITHUB_SERVER_INF = "https://raw.githubusercontent.com/damianlezcano/qfolder/jdk8-swing/p2p-server/dist/redirect.ref";
    public static String URL_SERVER;
    
    public static String WK_CREATE = "wk";
    public static String WK_CONNECT = "connect";
    public static String WK_RECONNECT = "reconnect";
    
    public static String WK_APPROVE_USER = "approved";
    public static String WK_REFUSE_USER = "refuse";
        
    public static String WK_CONNECT_WITHOUT_AUTH = "connect";
    public static String WK_CONNECT_WITH_AUTH = "connect/auth";
    
    //http client
    public static int HTTP_CLIENT_CONNECT_READ_TIMEOUT = 1000;
    public static int HTTP_CLIENT_CONNECT_REQUEST_TIMEOUT = 1000;

    public static String TEMP_PATH = "temp";
    public static String SUFFIX_PART = ".part";
    public static String SUFFIX_PENDING = ".pending";
    public static String SUFFIX_ENCODE = ".enc";
    public static String SUFFIX_DECODE = ".dec";
    
    public static String PREFFIX_ENCODE = "file";
    
    public static String buildWkCreateUri() {
        return String.format("%s/%s", URL_SERVER,WK_CREATE);
    }

    public static String buildWkConnectUri(Workspace wk, User user) {
        return String.format("%s/%s/%s?user=%s", URL_SERVER,wk.getId(),WK_CONNECT,user.getId());
    }
    
    public static String buildWkConnectWithoutAuthUri(Workspace wk) {
        return String.format("%s/%s/%s", URL_SERVER,wk.getId(),WK_CONNECT_WITHOUT_AUTH);
    }

    public static String buildWkConnectWithAuthUri(Workspace wk) {
        return String.format("%s/%s/%s", URL_SERVER,wk.getId(),WK_CONNECT_WITH_AUTH);
    }

    public static String buildWkSendApprovedUserUrl(Workspace wk, User user) {
        return String.format("%s/%s/%s/%s", URL_SERVER,wk.getId(),WK_APPROVE_USER,user.getId());
    }
    
    public static String buildWkSendRefuseUserUrl(Workspace wk, User user) {
        return String.format("%s/%s/%s/%s", URL_SERVER,wk.getId(),WK_REFUSE_USER,user.getId());
    }

    public static String buildWkBroadcastUri(Workspace wk) {
        return String.format("%s/%s", URL_SERVER,wk.getId());
    }
    
    public static String buildWkToUserUri(Workspace wk, User user){
        return String.format("%s/%s/%s", URL_SERVER,wk.getId(),user.getId());
    }
    
}
