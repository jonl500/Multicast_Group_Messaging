import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class Client
{
    /* how the server is gonna tell the difference between someone trying to join and a message to be multicasted */
    // "msg:xxx" tells the server that the input it has received is a message to be multicasted
    // "user:xxx:xxx" tells the server someone is trying to join. colon separates name and passcode
    // "leave:" tells the server that someone is requesting to leave

    //scanner
    static Scanner scan = new Scanner(System.in);

    //socket
    static Socket socket;

    //socket input and output
    static PrintWriter out;
    static BufferedReader in;

    //i know we agreed to keep the GUI for last, but i made this part of the GUI anyway
    //i did this because i was having a hard time figuring out how to keep printing messages while simultaneously expecting command line input
    static void messageGUI()
    {
        //create the frame
        JFrame frame = new JFrame("Multicast Group Messaging Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //layout
        MigLayout layout = new MigLayout();
        frame.setLayout(layout);

        //list for messages that have been sent
        DefaultListModel chatModel = new DefaultListModel();
        JList chat = new JList(chatModel);
        JScrollPane chatScroll = new JScrollPane();
        chatScroll.setViewportView(chat);

        //text field to enter a message
        JTextField messageField = new JTextField();

        //button to send a message
        JButton sendButton = new JButton("Send");

        //send button listener
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                //send the message
                sendMessage(messageField.getText());

                //clear the text field
                messageField.setText("");
            }
        });

        //button to leave
        JButton leaveButton = new JButton("Leave");

        //leave button listener
        leaveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try
                {
                    String leaveResponse = leave();
                    System.out.println(leaveResponse);
                    frame.dispose();
                }
                catch (IOException e)
                {
                    System.out.println("Error: Connection to the server has been lost.");
                    frame.dispose();
                }
            }
        });

        //timer for receiving messages
        Timer timer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try
                {
                    String message = receiveMessage();
                    chatModel.addElement(message);
                }
                catch (IOException e)
                {
                    System.out.println("Error: Connection to the server has been lost.");
                    frame.dispose();
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
        timer.start();
    }

    static void sendMessage(String message)
    {
        //i'm just going to assume that which message was sent by who, keeping track of names, etc. will all be handled by the server
        out.println("msg:" + message);
    }

    static String receiveMessage() throws IOException
    {
        //maybe the server can just concatenate a string for the client to print for each message
        //that way the client doesn't have to process it and can just print it
        //like "Name: message"
        //for example: "Dave: Hello!"
        //also use this for server messages, like "(name) has entered the chat."
        return in.readLine();
    }

    static String leave() throws IOException
    {
        //send request to the server to leave
        out.println("leave:");

        //get response to request
        return in.readLine();
    }

    static int connect(String name, String passcode) throws IOException
    {
        //IP address
        //just fill in the blank with the IP address of the machine that will run the server
        InetAddress address = InetAddress.getByName("");

        //port number
        //just change the "2770" with whichever port number we're actually gonna use
        int port = 2770;

        //establish connection to server
        socket = new Socket(address, port);

        //set up output
        out = new PrintWriter(socket.getOutputStream(), true);

        //set up input
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        //send the server the name and passcode that was entered
        out.println("user:" + name + ":" + passcode);

        //get a response from the server
        String response = in.readLine();

        //the server will deny access if the passcode is wrong or the name is already in use
        if(response.equals("wrongpasscode"))
        {
            socket.close();
            return 2;
        }
        else if(response.equals("duplicatename"))
        {
            socket.close();
            return 3;
        }

        //return 0 if there are no problems
        return 0;
    }

    static boolean logIn()
    {
        boolean loginSuccessful;

        //prompt the user for a display name
        System.out.print("Display Name: ");
        String name = scan.nextLine();

        //prompt the user for a passcode
        System.out.print("Passcode: ");
        String passcode = scan.nextLine();

        //try connecting to the server
        int connectionResult;
        try
        {
            connectionResult = connect(name, passcode);
        }
        catch (IOException e)
        {
            connectionResult = 1;
        }

        //check the connection result
        if (connectionResult == 0)
        {
            //connection succeeded
            loginSuccessful = true;
        }
        else if(connectionResult == 1)
        {
            //couldn't connect to server
            System.out.println("Error: Failed to connect to server.");
            loginSuccessful = false;
        }
        else if(connectionResult == 2)
        {
            //wrong passcode
            System.out.println("Error: You did not enter the correct passcode.");
            loginSuccessful = false;
        }
        else if(connectionResult == 3)
        {
            //name already in use
            System.out.println("Error: That display name is already in use.");
            loginSuccessful = false;
        }
        else
        {
            System.out.println("Error: An unknown error has occurred.");
            loginSuccessful = false;
        }

        return loginSuccessful;
    }

    public static void main(String[] args)
    {
        //try to log in
        boolean loginSuccessful = logIn();

        //check if the login was successful
        if(loginSuccessful)
        {
            //display the message GUI
            javax.swing.SwingUtilities.invokeLater(new Runnable()
            {
                public void run ()
                {
                    messageGUI();
                }
            });
        }
    }
}