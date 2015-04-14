a.The tcp is an easy one. My window size is 1. So I did not use command <window>. Checksum is used to handle the corrupt packet. 
  Seq# and Ack# are used to handle the reorder. There is a timer used to handle the loss.
  I used a 6M jpg containing 12511packets to test.It is OK. 
b.command:
  MyReceiver <filename> <listening_port> <sender_IP> <sender_port> <log_filename>
  MySender <filename> <remote_IP> <remote_port> <ack_port_num> <log_filename> <windowsize>
c.It can send jpg, txt, etc. It can show the retransmitted times.
  I send a packet with filename and filelength. This packet is used for a easy shake-hand. If the receiver send ACK, the sender start to send the file.


If there is any question, please contact me. Thank you so much.
