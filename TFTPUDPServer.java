package tftp.udp.server;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
   
/**
 * TFTP server built on UDP
 */
public class TFTPUDPServer extends Thread{

    protected DatagramSocket socket;
    
    public TFTPUDPServer() throws SocketException {
        this("UDPSocketServer");
    }
    
    public static void main(String[] args) throws IOException{
        new TFTPUDPServer().start();
    }

    public TFTPUDPServer(String name) throws SocketException{
        super(name);
        socket = new DatagramSocket(9000);
        socket.setSoTimeout(100000); //timeout
    }
    
    @Override
    public void run() {
        //runs forever unless error occurs
        try {
            while (true) {
                DatagramPacket packet = new DatagramPacket(new byte[516], 516);
                socket.receive(packet);
                System.out.println("Connection from: " + packet.getAddress() + ", " + packet.getPort() + "...");
                int opCode = (int) packet.getData()[1];//get opcode from client packet
                
                switch (opCode) {
                    case 1: //read request
                        System.out.println();
                        System.out.println("Read request received");
                        //gets file name from request data
                        byte[] dataFileName = packet.getData();
                        int endIndex = 0;

                        for(int i = 1;i<dataFileName.length-2;i++){
                            if(dataFileName[i] == 0){
                                endIndex = i;
                                break;
                            }
                        }
                        //get file name from request packet
                        dataFileName = Arrays.copyOfRange(packet.getData(),2,endIndex);
                        String fileName = new String(dataFileName);
                        writeFile(new File("src\\tftp\\udp\\server\\"+fileName),packet);
                        break;
                    case 2: //write request
                        System.out.println();
                        System.out.println("Write request received");
                        readFile(packet);
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println(e);
        }
    
    }
    
    /**
     * Sends file to client and checks data packet was acknowledged
     * 
     * @param file file to write to client
     * @param packet read request packet from client
     * @throws IOException 
     */ 
    private void writeFile(File file, DatagramPacket packet) throws IOException {
        //if file is not on the server error packet sent
        if(!file.exists()){
            sendError(packet,"File is not stored on the server".getBytes(), (byte) 1);
            System.out.println("File not on server");
        }

        else{
            Path path = Paths.get(file.getAbsolutePath());
            byte[] fileData = Files.readAllBytes(path); //converts file to byte array
            byte[] fileDataSub = fileData;
            boolean endOfFile = false; //end of file flag
            byte[] block = new byte[516]; //current block of data
            int blockNum = 1;
            
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
                sendPacket(block, packet);
                System.out.println("Data packet sent:"+blockNum);

                //receiving ack packet and timeout handling
                DatagramPacket ackPacket = new DatagramPacket(new byte[516], 516);
                while(true){
                    try{
                        socket.receive(ackPacket);
                        break;
                    }
                    //resend data if timeout occurs
                    catch(SocketTimeoutException e){
                        System.err.println("Timeout from server occured");
                        System.err.println("Resending packet");
                        sendPacket(block, packet);
                        continue;
                    }
                }

                //check if ack block number is correct
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
        }

    }
    
    /**
     * Sends given packet to client
     * 
     * @param data packet data to be sent
     * @param receivedPacket packet from client to get address and port
     * @throws IOException 
     */
    private void sendPacket(byte[] data, DatagramPacket receivedPacket) throws IOException{
        try{
            DatagramPacket packet = new DatagramPacket(data, data.length);
            packet.setAddress(receivedPacket.getAddress());
            packet.setPort(receivedPacket.getPort());
            socket.send(packet);
        }
        catch (IOException e) {
            System.err.println(e);
        }
    }  
        
    /**
     * Takes data packets and saves file to program source folder 
     * 
     * @param packet packet of data received
     * @throws IOException if timeout reached
     */
    private void readFile(DatagramPacket packet)throws IOException {
        //sending acknowledgement of write request (block number 0)
        sendACK(packet,new byte[]{0,0});
        
        //gets file name from request data
        byte[] dataFileName = packet.getData();
        int endIndex = 0;
        
        for(int i = 1;i<dataFileName.length-2;i++){
            if(dataFileName[i] == 0){
                endIndex = i;
                break;
            }
        }
        dataFileName = Arrays.copyOfRange(packet.getData(),2,endIndex);
        String fileName = new String(dataFileName);
        
        
        boolean endOfFile = false; //end of file flag
        byte[] dataFile = new byte[0]; //array containing all file data
        //loops until file transfer complete
        while(!endOfFile){
            //receiving data packet
            while(true){
                DatagramPacket packetReceived = new DatagramPacket(new byte[516], 516);
                try{
                    socket.receive(packetReceived);
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
                //if timeout occurs rewrite packet
                catch(SocketTimeoutException e){
                    System.err.println("Timeout from server occured");
                    System.err.println("Resending packet");
                    sendACK(packetReceived,new byte[]{packetReceived.getData()[2],packetReceived.getData()[3]});
                }
            }
        }
        System.out.println("End of file reached");
        //saving file
        try (FileOutputStream fos = new FileOutputStream("src\\tftp\\udp\\server\\"+fileName)) {
                fos.write(dataFile);
        }
        System.out.println("File saved: src\\tftp\\udp\\server\\"+fileName);
    }
    
    /**
     * Sends appropriate error packet to client
     * 
     * @param packetReceived packet from client
     * @param message error message
     * @param code error code
     * @throws IOException 
     */
    private void sendError(DatagramPacket packetReceived, byte[] message, byte code) throws IOException{
        byte[] error = concatenateArrays(new byte[]{0,5,0,code}, message);
        error = concatenateArrays(error,new byte[]{0});
        sendPacket(error,packetReceived);
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
     * Send acknowledgement packet to client
     * 
     * @param packet packet from client to get address and port
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
