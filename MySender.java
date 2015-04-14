
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MySender {

	private FileInputStream fileReader;
    private DatagramSocket sender;
    private int fileLength, currentPos, bytesRead, seq, ack, resendNum, estimateTime, sp, dp, flag, head, timeout;
    private byte[]  init, msg, buffer, sNum, dNum, seqNum, ackNum, len, checkNum, flush, data;
    private BufferedWriter bufferedWriter;
    private String ip;
//  private long estimateTime;
	public static void main(String[] args) throws IOException {
		
		File theFile = new File(args[0]);
		InetAddress theAddress = InetAddress.getByName(args[1]);
		int thePort = Integer.parseInt(args[2]);
		int ownPort = Integer.parseInt(args[3]);
		MySender client = new MySender(theAddress, thePort, ownPort, args[4]);
		String windowSize = args[5];
		client.sendFile(theFile);
		
	}
	
//Construct
    public MySender(InetAddress address, int port, int ownPort, String logFile) throws IOException{
    	ip = address.getHostAddress();
    	sp = ownPort;
    	dp = port;
    	msg = new byte[512];
    	buffer = new byte[532];
    	sNum = intToByte(ownPort, 2);
    	dNum = intToByte(port, 2);
    	flush = new byte[2];
    	data = new byte[532];   //20bytes means the header length
    	System.arraycopy(sNum,0,data,0,sNum.length);    	//put source port and dest port into data 
    	System.arraycopy(dNum,0,data,sNum.length,dNum.length);
    	estimateTime = 40;
    	sender = new DatagramSocket(ownPort);
    	//sender.connect(address, port);
    	bufferedWriter = new BufferedWriter(new FileWriter(logFile));
    }
//Function of sending the file
	public void sendFile(File theFile) throws IOException {
		// Init stuff
		try {
			fileReader = new FileInputStream(theFile);
			fileLength = fileReader.available();
		} catch (FileNotFoundException e1) {
			System.out.println("File not found. Please run the program again.");
			System.exit(0);
		}
		System.out.println(" -- Filename: " + theFile.getName());
		System.out.println(" -- Bytes to send: " + fileLength);

		// send the filename to the receiver
		init = (theFile.getName() + "::" + fileLength + "::").getBytes();
		System.arraycopy(flush, 0, data, 16, flush.length);
		System.arraycopy(init, 0, data, 20, init.length);
		checkNum = intToByte(checksum(data), 2);
		System.arraycopy(checkNum, 0, data, 16, checkNum.length);
		flush(checkNum);
		head = 1;

		DatagramPacket reply = new DatagramPacket(buffer, buffer.length);		
		while (currentPos < fileLength
				|| !(new String(reply.getData(), 0, reply.getLength())
						.equals("ACK"))) {
			sender.setSoTimeout(estimateTime); // set timer, when catch it comes back
			try {
				while (true) {
					while (head == 1) {
						if (head == 1) {
							send(InetAddress.getByName(ip), dp, data);
							flag = 1;
							sender.receive(reply);
							if (new String(reply.getData(), 0,
									reply.getLength()).equals("ACK")) {
								System.out
										.println(" -- Got ACK from receiver - sending the file ");
								flag = 0;
								head = 0;
								break;

							}
						}

					}
					if (new String(reply.getData(), 0, reply.getLength())
							.equals("ACK")) {
						reply.setData(new byte[532]);

						bytesRead = fileReader.read(msg);

						seqNum = intToByte(seq, 4);
						ackNum = intToByte(ack, 4);
						len = intToByte(20, 1);
						System.arraycopy(flush, 0, data, 16, flush.length);
						System.arraycopy(seqNum, 0, data, 4, seqNum.length);
						System.arraycopy(ackNum, 0, data, 8, ackNum.length);
						System.arraycopy(len, 0, data, 12, len.length);
						// System.arraycopy(flag, 0, data, 13, flag.length);
						System.arraycopy(msg, 0, data, 20, msg.length);
						checkNum = intToByte(checksum(data), 2);
						System.arraycopy(checkNum, 0, data, 16, checkNum.length);
						flush(checkNum);
						send(InetAddress.getByName(ip), dp, data);
						// System.out.println(seq);
						currentPos = currentPos + bytesRead;
						long sendTime = System.currentTimeMillis();
						SimpleDateFormat sdf = new SimpleDateFormat(
								"yyyy-MM-dd HH:mm:ss");
						String send_time = sdf.format(new Date());
						bufferedWriter.write(send_time.toString()
								+ " source:"
								+ InetAddress.getLocalHost().getHostAddress()
										.toString() + ":" + sp
								+ " destination:" + ip + ":" + dp + " SEQ#:"
								+ seq + " ACK#:" + ack + " EstimateRTT:"
								+ estimateTime + "ms" + "\r\n");
						bufferedWriter.flush();

						sender.receive(reply);
						long rcvTime = System.currentTimeMillis();
						estimateTime = (int) (0.875 * estimateTime + 0.125
								* (rcvTime - sendTime) + 20);
						// System.out.println(Long.toString(rcvTime -
						// sendTime));
						if (new String(reply.getData(), 0, reply.getLength())
								.equals("ACK")) {
							seq++;
							ack++;
						}
						// System.out.println(currentPos);
					} else { // resend when corrupted

						send(InetAddress.getByName(ip), dp, data);
						long sendTime = System.currentTimeMillis();
						// System.out.println("re" + seq);
						resendNum++;
						sender.receive(reply);
						long rcvTime = System.currentTimeMillis();

						estimateTime = (int) (0.875 * estimateTime + 0.125 * (rcvTime - sendTime));
						if (new String(reply.getData(), 0, reply.getLength())
								.equals("ACK")) {
							seq++;
							ack++;
						}
					}
					// System.out.println(new String(buffer,0,buffer.length));
					break;
				}
			} catch (SocketTimeoutException e) { // timeout
				// System.out.println("timeout");
				timeout++;
			}
		}
		System.out.println(" -- File transfer complete...");
		System.out.println(" -- Segments sent = " + seq);
		System.out.println(" -- Segments retransmitted (corrupt) = "
				+ resendNum);
		System.out.println(" -- Segments retransmitted (timeout) = " + timeout);
	}
	    
//little function of send packet
  public void send(InetAddress recv, int port,byte[] message)
       throws IOException {
      
	//InetAddress recv = InetAddress.getByName(host);
       DatagramPacket packet = 
	   new DatagramPacket(message, message.length, recv, port);
       sender.send(packet);
   }	
//little function of transferring int to byte
	    public byte[] intToByte(int num, int numLen){
	    	byte[] b = new byte[numLen];
	        for (int i = 0; i < b.length; i++) {
	            b[b.length - 1 - i] = (byte) (num >> 8 * i & 0xFF);
	        }
	        return b;
	    	
	    }
//little function of transferring byte to int
	    public int byteToInt(byte[] b) {
	        int num = 0;
	        byte bLoop;
	        for (int i = 0; i < b.length; i++) {
	            bLoop = b[i];
	            num += (bLoop & 0xFF) << (8 * (b.length - 1 - i));
	        }
	        return num;
	    }  
	    public byte[] flush(byte[] b){
	    	byte[] a = new byte[b.length];
	    	return a;
	    }
//little function of calculating checksum
	    public int checksum(byte[] data){
	    	int[] table = {
	                0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
	                0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440,
	                0xCC01, 0x0CC0, 0x0D80, 0xCD41, 0x0F00, 0xCFC1, 0xCE81, 0x0E40,
	                0x0A00, 0xCAC1, 0xCB81, 0x0B40, 0xC901, 0x09C0, 0x0880, 0xC841,
	                0xD801, 0x18C0, 0x1980, 0xD941, 0x1B00, 0xDBC1, 0xDA81, 0x1A40,
	                0x1E00, 0xDEC1, 0xDF81, 0x1F40, 0xDD01, 0x1DC0, 0x1C80, 0xDC41,
	                0x1400, 0xD4C1, 0xD581, 0x1540, 0xD701, 0x17C0, 0x1680, 0xD641,
	                0xD201, 0x12C0, 0x1380, 0xD341, 0x1100, 0xD1C1, 0xD081, 0x1040,
	                0xF001, 0x30C0, 0x3180, 0xF141, 0x3300, 0xF3C1, 0xF281, 0x3240,
	                0x3600, 0xF6C1, 0xF781, 0x3740, 0xF501, 0x35C0, 0x3480, 0xF441,
	                0x3C00, 0xFCC1, 0xFD81, 0x3D40, 0xFF01, 0x3FC0, 0x3E80, 0xFE41,
	                0xFA01, 0x3AC0, 0x3B80, 0xFB41, 0x3900, 0xF9C1, 0xF881, 0x3840,
	                0x2800, 0xE8C1, 0xE981, 0x2940, 0xEB01, 0x2BC0, 0x2A80, 0xEA41,
	                0xEE01, 0x2EC0, 0x2F80, 0xEF41, 0x2D00, 0xEDC1, 0xEC81, 0x2C40,
	                0xE401, 0x24C0, 0x2580, 0xE541, 0x2700, 0xE7C1, 0xE681, 0x2640,
	                0x2200, 0xE2C1, 0xE381, 0x2340, 0xE101, 0x21C0, 0x2080, 0xE041,
	                0xA001, 0x60C0, 0x6180, 0xA141, 0x6300, 0xA3C1, 0xA281, 0x6240,
	                0x6600, 0xA6C1, 0xA781, 0x6740, 0xA501, 0x65C0, 0x6480, 0xA441,
	                0x6C00, 0xACC1, 0xAD81, 0x6D40, 0xAF01, 0x6FC0, 0x6E80, 0xAE41,
	                0xAA01, 0x6AC0, 0x6B80, 0xAB41, 0x6900, 0xA9C1, 0xA881, 0x6840,
	                0x7800, 0xB8C1, 0xB981, 0x7940, 0xBB01, 0x7BC0, 0x7A80, 0xBA41,
	                0xBE01, 0x7EC0, 0x7F80, 0xBF41, 0x7D00, 0xBDC1, 0xBC81, 0x7C40,
	                0xB401, 0x74C0, 0x7580, 0xB541, 0x7700, 0xB7C1, 0xB681, 0x7640,
	                0x7200, 0xB2C1, 0xB381, 0x7340, 0xB101, 0x71C0, 0x7080, 0xB041,
	                0x5000, 0x90C1, 0x9181, 0x5140, 0x9301, 0x53C0, 0x5280, 0x9241,
	                0x9601, 0x56C0, 0x5780, 0x9741, 0x5500, 0x95C1, 0x9481, 0x5440,
	                0x9C01, 0x5CC0, 0x5D80, 0x9D41, 0x5F00, 0x9FC1, 0x9E81, 0x5E40,
	                0x5A00, 0x9AC1, 0x9B81, 0x5B40, 0x9901, 0x59C0, 0x5880, 0x9841,
	                0x8801, 0x48C0, 0x4980, 0x8941, 0x4B00, 0x8BC1, 0x8A81, 0x4A40,
	                0x4E00, 0x8EC1, 0x8F81, 0x4F40, 0x8D01, 0x4DC0, 0x4C80, 0x8C41,
	                0x4400, 0x84C1, 0x8581, 0x4540, 0x8701, 0x47C0, 0x4680, 0x8641,
	                0x8201, 0x42C0, 0x4380, 0x8341, 0x4100, 0x81C1, 0x8081, 0x4040,
	            };
	            
	            int checksum = 0x0000;
	            for (byte b : data) {
	                checksum = (checksum >>> 8) ^ table[(checksum ^ b) & 0xff];
	            }
	            return checksum;
	            
	    }

}
