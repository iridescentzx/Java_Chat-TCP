import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

public class MyReceiver {
	DatagramSocket s;
	String filename, initString;
	byte[] buffer, checkNum, seqNum, flush;
	DatagramPacket initPacket, receivedPacket;
	FileOutputStream fileWriter;
	int bytesReceived, bytesToReceive, oldCheck, newCheck, seqData, seqNow;

	public static void main(String[] args) throws IOException,
			InterruptedException {
		int ownPort = Integer.parseInt(args[1]);
		new MyReceiver(ownPort, args[0], args[2], args[3], args[4]);
	}

	public MyReceiver(int port, String file, String ip, String senderPort,
			String logFile) throws IOException, InterruptedException {
		// Init stuff
		s = new DatagramSocket(port);
		buffer = new byte[532];
		checkNum = new byte[2];
		flush = new byte[2];
		seqNum = new byte[4];
		System.out.println(" -- Ready to receive file on port: " + port);

		while (true) { 		
			initPacket = receivePacket();   //wait for a sender to transmit the filename and length
			System.arraycopy(initPacket.getData(), 16, checkNum, 0, 2);
			oldCheck = byteToInt(checkNum);
			flush(checkNum);
			flush(buffer);
			System.arraycopy(flush, 0, initPacket.getData(), 16, 2);
			newCheck = checksum(initPacket.getData());
			if (oldCheck == newCheck) {
				initString = new String(initPacket.getData(), 20,
						initPacket.getLength() - 20);
				StringTokenizer t = new StringTokenizer(initString, "::");
				filename = t.nextToken();
				bytesToReceive = new Integer(t.nextToken()).intValue();

				System.out
						.println(" -- The file will be saved as: " + filename);
				System.out.println(" -- Expecting to receive: "
						+ bytesToReceive + " bytes");
				send(InetAddress.getByName(ip), Integer.parseInt(senderPort),
						(new String("ACK")).getBytes());
				// System.out.println("send something to port: "+Integer.parseInt(senderPort));

				break;
			} else {
				send(InetAddress.getByName(ip), Integer.parseInt(senderPort),
						(new String("NAK")).getBytes());
			}
		}
		fileWriter = new FileOutputStream(file);
		BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(
				logFile));
		while (bytesReceived < bytesToReceive) {
			receivedPacket = receivePacket();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String rcv_time = sdf.format(new Date());

			System.arraycopy(receivedPacket.getData(), 4, seqNum, 0,
					seqNum.length);
			seqData = byteToInt(seqNum);
			System.arraycopy(receivedPacket.getData(), 16, checkNum, 0, 2);
			oldCheck = byteToInt(checkNum);
			flush(checkNum);
			System.arraycopy(flush, 0, receivedPacket.getData(), 16, 2);
			newCheck = checksum(receivedPacket.getData());
			// System.out.println(seqNow+"/"+seqData);
			if (oldCheck == newCheck) { //check corrupt
				// Thread.sleep(5000000);
				if (seqData == seqNow) { //check reorder
					fileWriter.write(receivedPacket.getData(), 20, receivedPacket.getLength() - 20); //write the received file by bytes
					bufferedWriter.write(rcv_time.toString()
							+ " source:"
							+ InetAddress.getLocalHost().getHostAddress()
									.toString() + ":" + port + " destination:"
							+ ip + ":" + senderPort + " SEQ#:" + seqData
							+ " ACK#:" + seqNow + "\r\n");  // write logfile
					bufferedWriter.flush();
					send(InetAddress.getByName(ip),
							Integer.parseInt(senderPort),
							(new String("ACK")).getBytes());

					bytesReceived = bytesReceived + receivedPacket.getLength()
							- 20;
					seqNow++;
				}

			}

			else {
				send(InetAddress.getByName(ip), Integer.parseInt(senderPort),
						(new String("NAK")).getBytes());
			}
		}
		bufferedWriter.close();
		System.out.println(" -- Delivery completed successfully --");
	}
    
//little function of receive packet
    public DatagramPacket receivePacket() throws IOException{
	
	DatagramPacket packet = 
	    new DatagramPacket(buffer, buffer.length);
	s.receive(packet);
	
	return packet;
    }

//little function of send packet
    public void send(InetAddress recv, int port,byte[] message)
       throws IOException {
       DatagramPacket packet = 
	   new DatagramPacket(message, message.length, recv, port);
       s.send(packet);
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
    	b = new byte[b.length];
    	return b;
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


