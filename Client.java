/*
 * CSC 445 Assignment 3 - Group Messaging Application
 * Client
 */
package assignment3;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.miginfocom.swing.MigLayout;

public class Client {

    /* how the server is gonna tell the difference between someone trying to join and a message to be multicasted */
    // "msg:xxx" tells the server that the input it has received is a message to be multicasted
    // "user:xxx:xxx" tells the server someone is trying to join. colon separates name and passcode
    // "leave:" tells the server that someone is requesting to leave
    //scanner
    static Scanner scan = new Scanner(System.in);

    //socket
    static MulticastSocket socket;
    //IP address
    static InetAddress group;
    static InetAddress address;
    //port number
    static int port;

    // parameters for encrytion
    static BigInteger p = BigInteger.valueOf(2147483647);    // Integer.MAX_VALUE and prime
    static BigInteger g = BigInteger.valueOf(7);
    static int a;     // random for every message
    static BigInteger A;
    static BigInteger B;
    static BigInteger C;
    static byte[] key;

    // for sending messages
    static String separator = "|00|";
    static String parseSeparator = "\\|00\\|";

    // assigned client ID from server, use throughout the process
    static String clientID;

    // one and only passcode
    static String passcode;

    //gui components
    static JFrame frame;
    static JScrollPane chatScroll;
    static JTextField messageField;
    static JButton sendButton;
    static JButton leaveButton;

    //i know we agreed to keep the GUI for last, but i made this part of the GUI anyway
    //i did this because i was having a hard time figuring out how to keep printing messages while simultaneously expecting command line input
    static void messageGUI() {
        //create the frame
        frame = new JFrame("Multicast Group Messaging Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //layout
        MigLayout layout = new MigLayout();
        frame.setLayout(layout);

        //list for messages that have been sent
        DefaultListModel chatModel = new DefaultListModel();
        JList chat = new JList(chatModel);
        chatScroll = new JScrollPane();
        chatScroll.setViewportView(chat);

        //text field to enter a message
        messageField = new JTextField();

        //button to send a message
        sendButton = new JButton("Send");

        //send button listener
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    //send the message
                    sendMessage(messageField.getText());
                } catch (IOException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                }
                //clear the text field
                messageField.setText("");

                //redraw gui
                redrawGui();
            }
        });

        //button to leave
        leaveButton = new JButton("Leave");

        //leave button listener
        leaveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    String leaveResponse = leave();
                    leaveResponse = interpretMessage(leaveResponse);
                    if (leaveResponse.equals("END")) {
                        System.out.println(leaveResponse);
                    }
                    frame.dispose();
                } catch (IOException e) {
                    System.out.println("Error: Connection to the server has been lost.");
                    frame.dispose();
                }
            }
        });

        //timer for receiving messages
        Timer timer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    String message = receiveMessage();
                    String element = interpretMessage(message);
                    chatModel.addElement(element);
                    redrawGui();
                } catch (IOException e) {
                    //
                }
            }
        });

        //add all the components to the frame
        frame.add(chatScroll, "cell 0 0, width 400:400:400, height 200:200:200");
        frame.add(messageField, "cell 0 1, width 400:400:400");
        frame.add(sendButton, "cell 0 2, width 100:100:100, height 25:25:25, gapbottom 50");
        frame.add(leaveButton, "cell 0 3, width 100:100:100, height 25:25:25");

        //draw the frame
        frame.setSize(500, 400);
        frame.setResizable(false);
        frame.setVisible(true);

        //start the timer
        timer.setRepeats(true);
        timer.start();
    }

    static void redrawGui()
    {
        //clear frame

        //add all the components to the frame
        frame.add(chatScroll, "cell 0 0, width 400:400:400, height 200:200:200");
        frame.add(messageField, "cell 0 1, width 400:400:400");
        frame.add(sendButton, "cell 0 2, width 100:100:100, height 25:25:25, gapbottom 50");
        frame.add(leaveButton, "cell 0 3, width 100:100:100, height 25:25:25");

        //draw the frame
        frame.setSize(500, 400);
        frame.setResizable(false);
        frame.setVisible(true);
    }

    static void sendMessage(String message) throws IOException {
        //i'm just going to assume that which message was sent by who, keeping track of names, etc. will all be handled by the server
        byte[] buf = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        socket.send(packet);
    }

    static String receiveMessage() throws IOException {
        // just receiving, interpret separately
        byte[] receiveBuffer = new byte[1000];
        DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(packet);
        byte[] received = packet.getData();
        String response = new String(received).trim();
        return response;
    }

    static void receiveKey() throws IOException {
        // just receiving, interpret separately
        byte[] receiveBuffer = new byte[4];
        DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(packet);
        byte[] received = packet.getData();
        B = BigInteger.valueOf(ByteBuffer.wrap(received).getInt());
        System.out.println(B.intValue());
    }

    static String leave() throws IOException {
        //send request to the server to leave
        sendMessage(leaveRequestMessage(clientID));
        //get response to request
        return receiveMessage();
    }

    static int connect(String name, String passcode) throws IOException {

        sendMessage(joinRequestMessage(passcode, name));

        //get a response from the server
        String response = receiveMessage();
        response = interpretMessage(response);

        //the server will deny access if the passcode is wrong or the name is already in use
        if (response.contains("Error")) {
            socket.close();
            System.out.println(response);
            return 2;
        }

        //return 0 if there are no problems
        clientID = response;
        return 0;
    }

    static boolean logIn() {
        boolean loginSuccessful;

        //prompt the user for a display name
        System.out.print("Display Name: ");
        String name = scan.nextLine();

        //prompt the user for a passcode
        System.out.print("Passcode: ");
        String code = scan.nextLine();
        passcode = code;

        //try connecting to the server
        int connectionResult;
        try {
            connectionResult = connect(name, passcode);
        } catch (IOException e) {
            connectionResult = 1;
        }

        //check the connection result
        if (connectionResult == 0) {
            //connection succeeded
            loginSuccessful = true;
        } else if (connectionResult == 1) {
            //couldn't connect to server
            System.out.println("Error: Failed to connect to server.");
            loginSuccessful = false;
        } else if (connectionResult == 2) {
            // an error happened
            loginSuccessful = false;
        } else {
            System.out.println("Error: An unknown error has occurred.");
            loginSuccessful = false;
        }

        return loginSuccessful;
    }

    static String joinRequestMessage(String passcode, String username) throws IOException {
        generateRandomKey();     // random key for each message
        String publicKey = Arrays.toString(ByteBuffer.allocate(4).putInt(A.intValue()).array());
        publicKey = publicKey.substring(1, publicKey.length() - 1);
        String type = "1";
        System.out.println(type + separator + passcode + separator + username);
        return publicKey + separator + encrypted(type + separator + passcode + separator + username);
    }

    static String generalMessage(String clientID, String msg) throws IOException {
        generateRandomKey();     // random key for each message
        String publicKey = Arrays.toString(ByteBuffer.allocate(4).putInt(A.intValue()).array());
        publicKey = publicKey.substring(1, publicKey.length() - 1);
        String type = "2";
        System.out.println(type + separator + clientID + separator + msg);
        return publicKey + separator + encrypted(type + separator + clientID + separator + msg);
    }

    static String leaveRequestMessage(String clientID) throws IOException {
        generateRandomKey();     // random key for each message
        String publicKey = Arrays.toString(ByteBuffer.allocate(4).putInt(A.intValue()).array());
        publicKey = publicKey.substring(1, publicKey.length() - 1);
        String type = "3";
        System.out.println(type + separator + clientID);
        return publicKey + separator + encrypted(type + separator + clientID);
    }

    static String interpretMessage(String message) {
        String msg = decrypted(message);
        String[] parsed = msg.split(parseSeparator);
        String type = parsed[0];
        switch (type) {
            case "1":
                return parsed[1];     // client ID
            case "2":
                return parsed[1] + ": " + parsed[2];     // username: massage
            case "3":
                return parsed[1];     // END
            case "4":
                return "Error " + parsed[1] + ": " + parsed[2];     // Error [errCode]: errMessage
            case "5":
                return parsed[1];
            default:
                return "Unknown message";
        }
    }

    static void generateRandomKey() {
        a = (int) (Math.random() * 100) + 10;
        A = g.pow(a).mod(p);
        C = B.pow(a).mod(p);
        key = ByteBuffer.allocate(4).putInt(C.intValue()).array();
        System.out.println(Arrays.toString(key));
    }

    static String encrypted(String message) throws IOException {
        int size = message.length();
        if (size % 4 != 0) {
            size = size + (4 - size % 4);     // to make the size multiple of 4, easier to parse integer
        }
        byte[] encryptedBytes = new byte[size];
        int intKey = ByteBuffer.wrap(key).getInt();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(message.getBytes());
        for (int i = 0; i < (4 - size % 4); i++) {
            b.write((byte) 0);
        }
        ByteBuffer buffer = ByteBuffer.wrap(b.toByteArray());
        for (int i = 0; i < size; i = i + 4) {
            int intMessage = buffer.getInt();
            int xor = intMessage ^ intKey;
            byte[] encryptedPart = ByteBuffer.allocate(4).putInt(xor).array();
            System.arraycopy(encryptedPart, 0, encryptedBytes, i, 4);
        }
        String encrypted = Arrays.toString(encryptedBytes);
        encrypted = encrypted.substring(1, encrypted.length() - 1);
        return encrypted;
    }

    static String decrypted(String message) {
        String key = passcode;
        while (message.length() > key.length()) {
            key = key + key;
        }
        int i = 0;
        String decrypted = "";
        String[] parsed = message.split(",");
        for (i = 0; i < parsed.length; ++i) {
            int e = Integer.parseInt(parsed[i]);
            int k = (int) key.charAt(i);
            int msg = e ^ k;
            decrypted = decrypted + (char) msg;
        }
        return decrypted;
    }

    public static void main(String[] args) throws UnknownHostException, IOException {

        //IP address
        //just fill in the blank with the IP address of the machine that will run the server
        group = InetAddress.getByName("230.0.0.0");
        address = InetAddress.getByName("pi.cs.oswego.edu");

        //port number
        //just change the "2770" with whichever port number we're actually gonna use
        port = 2770;

        //establish connection to server
        socket = new MulticastSocket(port);
        socket.joinGroup(group);

        // ask server for encryption public key
        sendMessage("Key Request");
        receiveKey();

        //try to log in
        boolean loginSuccessful = logIn();

        //set socket timeout
        socket.setSoTimeout(100);

        //check if the login was successful
        if (loginSuccessful) {
            //display the message GUI
            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    messageGUI();
                }
            });
        }
    }

}
