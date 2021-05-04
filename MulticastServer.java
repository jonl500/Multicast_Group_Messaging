/*
 * CSC 445 Assignment 3 - Group Messaging Application
 * Multicasting Server
 */
package assignment3;

import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;

public class MulticastServer {

    HashMap<String,String> clients;     // ID -> username
    
    //socket
    static MulticastSocket socket;
    //IP address
    static InetAddress address;
    //port number
    static int port;
    
    // for sending messages
    static String separator = "|00|";
    static String parseSeparator = "\\|00\\|";
    
    public static void main(String[] args) {
        // we may have to do a multicast socket and a datagram socket
        // one for sending things to everybody, one for sending things to spcific clients
        // only general messages, join/leave notification, should be multicasted
        // join/leave response, error messages, should be directed
    }
    
}
