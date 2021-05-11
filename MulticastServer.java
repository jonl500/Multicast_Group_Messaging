/*
 * CSC 445 Assignment 3 - Group Messaging Application
 * Multicasting Server
 */
package assignment3;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;

public class MulticastServer {
    
    static HashMap<String, String> clients;     // ID -> username

    //socket
    static DatagramSocket socket;
    //IP address
    static InetAddress group;
    static InetAddress address;
    //port number
    static int port;

    // for sending messages
    static String separator = "|00|";
    static String parseSeparator = "\\|00\\|";

    // parameters for encrytion
    static BigInteger p;
    static BigInteger g;
    static int b;     // random at start
    static BigInteger A;
    static BigInteger B;
    static BigInteger C;
    static byte[] key;

    // one and only passcode
    static String passcode;
    
    public static void main(String[] args) throws IOException {
        group = InetAddress.getByName("230.0.0.0");     // where we run our server
        port = 2770;
        b = (int) (Math.random() * 100) + 10;
        B = g.pow(b).mod(p);
        passcode = "";     // what do you guys want it to be?
        while (true) {
            String received = receiveMessage();
            if (received.equals("Key Request")) {
                byte[] sendKey = ByteBuffer.allocate(4).putInt(B.intValue()).array();
                DatagramPacket packet = new DatagramPacket(sendKey, sendKey.length, address, port);
                socket.send(packet);
            } else {
                interpretMessage(received);
            }
        }
        // if we make a GUI for this we can have a close button to close the server
        // socket.close();
    }
    
    private static void sendMessage(String message) throws IOException {
        //i'm just going to assume that which message was sent by who, keeping track of names, etc. will all be handled by the server
        byte[] buf = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        socket.send(packet);
    }
    
    private static void multicastMessage(String message) throws IOException {
        byte[] buf = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, port);
        socket.send(packet);
    }
    
    static String receiveMessage() throws IOException {
        // just receiving, interpret separately
        byte[] receiveBuffer = new byte[1000];
        DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(packet);
        address = packet.getAddress();
        byte[] received = packet.getData();
        String response = new String(received).trim();
        return response;
    }
    
    private static String encrypted(String message) {
        String key = passcode;
        while (message.length() > key.length()) {
            key = key + key;
        }
        int i = 0;
        String encrypted = "";
        for (i = 0; i < message.length(); ++i) {
            int msg = (int) message.charAt(i);
            int k = (int) key.charAt(i);
            int e = msg ^ k;
            encrypted = encrypted + String.valueOf(e) + ",";
        }
        return encrypted.substring(0, encrypted.length() - 1);
    }
    
    private static String decrypted(String message) {
        String[] split = message.split(",");
        byte[] buf = new byte[split.length];
        for (int i = 0; i < split.length; i++) {
            byte b = Byte.parseByte(split[i]);
            buf[i] = b;
        }
        byte[] decryptedBytes = new byte[buf.length];
        int intKey = ByteBuffer.wrap(key).getInt();
        ByteBuffer buffer = ByteBuffer.wrap(buf);
        for (int i = 0; i < buf.length; i = i + 4) {
            int intMessage = buffer.getInt();
            int xor = intMessage ^ intKey;
            byte[] decryptedPart = ByteBuffer.allocate(4).putInt(xor).array();
            System.arraycopy(decryptedPart, 0, decryptedBytes, i, 4);
        }
        String decrypted = new String(decryptedBytes).trim();
        return decrypted;
    }
    
    private static void interpretMessage(String msg) throws IOException {
        String[] parsed = msg.split(parseSeparator);
        String type = parsed[0];
        switch (type) {
            case "1":
                receiveKey(parsed[1]);     // A key
                if (verifyPasscode(parsed[2])) {
                    String username = getName(parsed[3]);
                    String clientID = generateID();
                    clients.put(clientID, username);
                    sendMessage(joinRequestResponse(clientID));
                    multicastMessage(joinBroadcast(clientID));
                } else {
                    // wrong passcode
                    sendMessage(errorMessage(403, "Forbidden: Invalid passcode."));
                }
            case "2":
                receiveKey(parsed[1]);     // A key
                String clientID = getID(parsed[2]);
                if (clients.containsKey(clientID)) {
                    String message = decrypted(parsed[3]);
                    multicastMessage(generalMessage(clientID, message));
                } else {
                    // client is not in the list, invalid
                    sendMessage(errorMessage(401, "Unauthorized: Client not in group."));
                }
            case "3":
                receiveKey(parsed[1]);     // A key
                String id = getID(parsed[2]);
                if (clients.containsKey(id)) {
                    sendMessage(leaveRequestResponse());
                    multicastMessage(leaveBroadcast(id));
                } else {
                    // client is not in the list, invalid
                    sendMessage(errorMessage(401, "Unauthorized: Client not in group."));
                }
            default:
                sendMessage(errorMessage(400, "Bad Request: Invalid message format."));
        }
    }
    
    private static void receiveKey(String publicKey) {
        String[] split = publicKey.split(",");
        byte[] buf = new byte[split.length];
        for (int i = 0; i < split.length; i++) {
            byte b = Byte.parseByte(split[i]);
            buf[i] = b;
        }
        A = BigInteger.valueOf(ByteBuffer.wrap(buf).getInt());
        C = A.pow(b).mod(p);
        key = ByteBuffer.allocate(4).putInt(C.intValue()).array();
    }
    
    private static boolean verifyPasscode(String encryptedPC) {
        String receivedPC = decrypted(encryptedPC);
        return (receivedPC.equals(passcode));
    }
    
    private static String getName(String encryptedName) {
        return decrypted(encryptedName);
    }
    
    private static String generateID() {
        String characters = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
        String randomKey = "";
        for (int i = 0; i < 20; i++) {
            int randomIndex = (int) (characters.length() * Math.random());
            randomKey = randomKey + characters.charAt(randomIndex);
        }
        return randomKey;
    }
    
    private static String joinRequestResponse(String clientID) {
        String type = "1";
        String encryptedID = encrypted(clientID);
        return type + separator + encryptedID;
    }
    
    private static String errorMessage(int errCode, String errMsg) {
        String type = "4";
        return type + separator + errCode + separator + errMsg;
    }
    
    private static String getID(String encryptedID) {
        return decrypted(encryptedID);
    }
    
    private static String leaveRequestResponse() {
        String type = "3";
        return type + separator + "END";
    }
    
    private static String joinBroadcast(String clientID) {
        String type = "5";
        String username = clients.get(clientID);
        String msg = encrypted(" [ " + username + " ] has entered the chat.");
        return type + separator + msg;
    }
    
    private static String leaveBroadcast(String clientID) {
        String type = "5";
        String username = clients.get(clientID);
        String msg = encrypted(" [ " + username + " ] has left the chat.");
        return type + separator + msg;
    }
    
    private static String generalMessage(String clientID, String message) {
        String type = "2";
        String id = encrypted(clientID);
        String msg = encrypted(message);
        return type + separator + id + separator + msg;
    }
}
