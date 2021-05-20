package tftp.udp.client;
import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Scanner;
/**
 * TFTP client built on top of UDP
 */
public class TFTPUDPClient {
  
    private InetAddress inetAddress;
    protected DatagramSocket socket;

    public TFTPUDPClient(String address, int port) throws IOException{
        inetAddress = InetAddress.getByName(address);
        socket = new DatagramSocket(port);
        socket.setSoTimeout(10000); //timeout
    }

    public static void main(String[] args) throws IOException, FileNotFoundException{
        String address = args[0]; //ip address from parameters
        int port = Integer.parseInt(args[1]); //port number from parameters
        
        //check for correct number of arguments
        if (args.length != 2) {
            System.err.println("Usage: java TFTPUDPClient <address> <port>");
            System.exit(1);
        }
        
        int choice = 0; //user menu choice
        Scanner input = new Scanner(System.in);
        while(true){
            boolean valid = false; //valid input flag
            do {
                input = new Scanner(System.in);
                try{
                    //menu
                    System.out.println();
                    System.out.println("1 - Write file to server");
                    System.out.println("2 - Read file from server");
                    System.out.println("3 - Exit");
                    choice = input.nextInt();
                    if (choice == 1 || choice == 2 || choice == 3){
                        valid = true;
                        //exit program if user quits
                        if (choice == 3){
                            System.exit(0);
                        }
                    }
                    else{
                        throw new Exception();
                    }
                }
                //invalid user input handling
                catch(Exception e){
                    System.out.println();
                    System.out.println("Only enter 1, 2 or 3");
                }
            } while (!valid);

            valid = false; //valid file entered
            String fileName = "";
            File file = null;
            do {
                try{
                    input = new Scanner(System.in);
                    System.out.println("Enter file name with extension");
                    fileName = input.nextLine();
                    if (choice == 1){
                        file = new File("src\\tftp\\udp\\client\\"+fileName);
                        if (!Files.exists(Paths.get(file.getAbsolutePath()))){
                            throw new FileNotFoundException();
                        }  
                    }
                    valid = true;
                }
                catch(FileNotFoundException e){
                    System.out.println();
                    System.out.println("File not found");
                }
            } while (!valid);

            TFTPUDPClient client = new TFTPUDPClient(address,port);
            byte[] request;
            switch (choice) {
                case 1: //write file to server (send request)
                    request = client.makeRequest((byte) 2, file.getName());
                    client.sendPacket(request);
                    System.out.println("WRQ sent");
                    System.out.println("");
                    System.out.println("Sending file... "+file.getName());
                    client.writeFile(file);
                    break;
                case 2: //read file from server (send request)
                    request = client.makeRequest((byte) 1, fileName);
                    client.sendPacket(request);
                    System.out.println("RRQ sent");
                    client.readFile(fileName);
                    break;
            }
        }
    }
        
    /**
     * Takes data packets and saves file to program source folder 
     * 
     * @param packet packet of data received
     * @throws IOException if timeout reached
     */
    private void readFile(String fileName)throws IOException {
        boolean endOfFile = false; //end of file flag
        byte[] dataFile = new byte[0]; //array containing all file data
        
        //loops until file transfer complete
        while(!endOfFile){
            //receiving data packet
            while(true){
                DatagramPacket packetReceived = new DatagramPacket(new byte[516], 516);
                try{
                    socket.receive(packetReceived);
                    if(packetReceived.getData()[1] == 5){
                        errorHandling(packetReceived);
                    }
                    //get file data without opcode and block number
                    byte[] data = Arrays.copyOfRange(packetReceived.getData(),4,packetReceived.getLength()); 
                    dataFile = concatenateArrays(dataFile,data);

                    //write ack to client with block number
                    sendACK(packetReceived,new byte[]{packetReceived.getData()[2],packetReceived.getData()[3]});

                    System.out.println("Block "+ (packetReceived.getData()[2] + packetReceived.getData()[3])+" received");

                    //check if end of file reached
                    if (data.length < 512){
                        endOfFile = true;
                    }
                    break;
                }
                
                //if timeout occurs resend packet
                catch(SocketTimeoutException e){
                    System.err.println("Timeout from server occured");
                    System.err.println("Resending packet...");
                    sendACK(packetReceived,new byte[]{packetReceived.getData()[2],packetReceived.getData()[3]});
                }
            }         
        }
        System.out.println("End of file reached");
        //saving file
        try (FileOutputStream fos = new FileOutputStream("src\\tftp\\udp\\client\\"+fileName)) {
                fos.write(dataFile);
        }
        System.out.println("File saved: src\\tftp\\udp\\client\\"+fileName);
        socket.close();
    }
    
    /**
     * Sends file to server and checks data packet was acknowledged
     * 
     * @param file file to write to server
     * @param packet read request packet from server
     * @throws IOException 
     */ 
    private void writeFile(File file) throws IOException {
        Path path = Paths.get(file.getAbsolutePath());
        byte[] fileData = Files.readAllBytes(path);
        byte[] fileDataSub = fileData;
        byte[] block = new byte[516];//current block of data
        boolean endOfFile = false;//end of file flag
        int blockNum = 1;

        //receiving initial ack packet with block number 0
        DatagramPacket packet = new DatagramPacket(new byte[516], 516);
        while(true){
            try{
                socket.receive(packet);
                break;
            }
            catch(SocketTimeoutException e){
                System.err.println("Timeout from server occured");
                System.err.println("Resending packet");
                sendPacket(packet.getData());
                continue;
            }
        }

        while(!endOfFile){
            int endIndex = 0;
            //takes first 512 bytes of the file
            if (fileData.length > 512){
                fileDataSub = Arrays.copyOfRange(fileData,0,512);
                endIndex = 512;
                block = new byte[516];
            }

            else{
                fileDataSub = Arrays.copyOfRange(fileData,0,fileData.length);
                endIndex = fileData.length;
                block = new byte[fileData.length+4];
                endOfFile = true;
            }

            //if both the block number bytes needs to be used
            if(blockNum >=255){ //resets block number of max value with two bytes is reached
                blockNum = 0;
                block[2] = (byte) 0;
                block[3] = (byte) blockNum;
            }
            else if (blockNum > 127){
                block[2] = (byte) (blockNum-127);
                block[3] = (byte) 127;
            }
            else{
                block[2] = (byte) 0;
                block[3] = (byte) blockNum;
            }
            
            //file data for current block
            fileData = Arrays.copyOfRange(fileData,endIndex,fileData.length);
            
            //opcode added to data packet
            block[0]=0;
            block[1]=3;

            //add data block to current data packet
            for(int i = 4;i<fileDataSub.length+4;i++){
                block[i] = fileDataSub[i-4];
            }

            //send data packet
            sendPacket(block);
            System.out.println("Data packet sent:"+blockNum);

            //receiving ack packet and timeout handling
            DatagramPacket ackPacket = new DatagramPacket(new byte[516], 516);
            while(true){
                try{
                    socket.receive(ackPacket);
                    break;
                }
                
                catch(SocketTimeoutException e){
                    System.err.println("Timeout from server occured");
                    System.err.println("Resending packet");
                    sendPacket(packet.getData());
                }
            }

            byte[] ackPacketData = ackPacket.getData();
            if(ackPacketData[1] == 4){
                int packetBlock = ackPacketData[2]+ackPacketData[3];
                System.out.println("Data packet acked:"+packetBlock);
                if (packetBlock != blockNum && blockNum >= 0){
                    throw new IOException();
                }
                blockNum++;
            }
        }
        System.out.println("File sent");
        socket.close();
    }
    
    /**
     * Sends given packet to server
     * 
     * @param data packet data to be sent
     * @throws IOException 
     */
    private void sendPacket(byte[] data) throws IOException{
        try{
            DatagramPacket packet = new DatagramPacket(data, data.length);
            packet.setAddress(inetAddress);
            packet.setPort(9000);
            socket.send(packet);
        } catch (UnknownHostException e) {
            System.err.println("Could not find host: " + inetAddress);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Could not connect to: " + inetAddress);
            System.exit(1);
        }
    }

    /**
     * Handles error packet from server
     * 
     * @param packet packet with opcode 5 (error packet)
     */
    private void errorHandling(DatagramPacket packet){
        System.out.println();
        String message = new String(Arrays.copyOfRange(packet.getData(),4,packet.getLength()));
        System.err.println(message);
        socket.close();
        System.exit(0);
    }

    /**
     * Creates read/write request packet
     * 
     * @param opCode read or write opcode
     * @param fileName 
     * @return request packet ready to be sent
     */
    private byte[] makeRequest(byte opCode, String fileName){
        byte[] request = new byte[9 + fileName.length()];
        int index = 0;
        String mode = "octet";

        request[index] = 0;
        index++;

        request[index] = opCode;
        index++;

        //file name added to packet
        for(int i = 0; i<fileName.length();i++){
            request[index] = (byte) fileName.charAt(i);
            index++;
        }

        request[index] = 0;
        index++;

        //mode added to packet
        for(int i = 0; i < mode.length();i++){
            request[index] = (byte) mode.charAt(i);
            index++;
        }

        request[index] = 0;
        return request;
    }
    
    /**
     * Concatenates the two arrays given
     * 
     * @param a first array
     * @param b second array
     * @return a and b arrays concatenated
     */
    private byte[] concatenateArrays(byte[] a, byte[] b){
        byte[] combined = new byte[a.length+b.length];
        
        int index = 0;
        for (byte element : a) {
            combined[index] = element;
            index++;
        }

        for (byte element : b) {
            combined[index] = element;
            index++;
        }
        return combined; 
    }
    
    /**
     * Send acknowledgement packet to server
     * 
     * @param packet packet from server to get address and port
     * @param blocks block number for data packet to be acknowledged
     * @throws IOException 
     */
    private void sendACK(DatagramPacket packet, byte[] blocks) throws IOException {
        byte[] ACK = { 0, 4, blocks[0], blocks[1]};
        DatagramPacket ack = new DatagramPacket(ACK, ACK.length, packet.getAddress(), packet.getPort());
        try {
            socket.send(ack);
        } 
        catch (IOException e) {
            System.err.println(e);
        }
    }

}
