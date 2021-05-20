package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Date;

public class UDPSocketServer extends Thread {

    protected DatagramSocket socket = null;

    public UDPSocketServer() throws SocketException {
        this("UDPSocketServer");
    }

    public UDPSocketServer(String name) throws SocketException {
        super(name);
        // **********************************************
        // Add a line here to instantiate a DatagramSocket for the socket field defined above.
        // Bind the socket to port 9000 (any port over 1024 would be ok as long as no other application uses it).
        // Ports below 1024 require administrative rights when running the applications.
        // Take a note of the port as the client needs to send its datagram to an IP address and port to which this server socket is bound.
        //***********************************************

        socket = new DatagramSocket(9000);

    }

    @Override
    public void run() {

        int counter = 0;                    // just a counter - used below
        byte[] recvBuf = new byte[256];     // a byte array that will store the data received by the client

        try {
            // run forever
            while (true) {
                //**************************************
                // Add source code below to: 
                // 1) create a DatagramPacket called packet. Use the byte array above to construct the datagram
                // 2) wait until a client sends something (a blocking call).
                //**************************************

                DatagramPacket packet = new DatagramPacket(recvBuf, 256);
                socket.receive(packet);

                // Get the current date/time and copy it in the byte array
                String dString = new Date().toString() + " - Counter: " + (counter);
                int len = dString.length();                                             // length of the byte array
                byte[] buf = new byte[len];                                             // byte array that will store the data to be sent back to the client
                System.arraycopy(dString.getBytes(), 0, buf, 0, len);

                //****************************************
                // Add source code below to extract the IP address (an InetAddress object) and source port (int) from the received packet
                // They will be both used to send back the response (which is now in the buf byte array -- see above)
                //****************************************
                InetAddress addr = packet.getAddress();
                int srcPort = packet.getPort();

                // set the buf as the data of the packet (let's re-use the same packet object)
                packet.setData(buf);
                
                // set the IP address and port extracted above as destination IP address and port in the packet to be sent
                packet.setAddress(addr);
                packet.setPort(srcPort);

                //*****************************************
                // Add a line below to send the packet (a blocking call)
                //*****************************************
                socket.send(packet);

                counter++;
            }
        } catch (IOException e) {
            System.err.println(e);
        }
        socket.close();
    }

    public static void main(String[] args) throws IOException {
        new UDPSocketServer().start();
        System.out.println("Time Server Started");
    }

}
