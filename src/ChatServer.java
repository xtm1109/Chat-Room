/**
 *  
 * @author Xuan Mai
 * @since 04/11/2016
 * 
 * 
 * Chat server listens on port 1337.
 * 
 * This is a chat room server that will listen to different connection
 * and display message between clients
 *
 * Each client is serviced in a separate thread.
 *
 */

import java.net.*;
import java.io.*;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.concurrent.*;

public class ChatServer {
	private static final int PORT = 1337;
	private static final Executor exec = Executors.newCachedThreadPool();
	private static ArrayList<Connection> allSockets = new ArrayList<Connection>();

	public static void main(String[] args) throws IOException {
		ServerSocket sock = null;
		
		try {
			sock = new ServerSocket(PORT); // create a server socket listening to port 1337
			
			while (true) {
				Runnable task = new Connection(sock.accept()); //listen for connections
				exec.execute(task); //service the connection in a separate thread
			}
		} catch (IOException ioe) {
			System.err.println(ioe);
		} finally {
			if (sock != null)
				sock.close();
		}
	}
	
	public static class Connection implements Runnable {
		String username;
		boolean running;
		
		Socket client;
		BufferedReader from_client; 	// Read from client
		PrintWriter to_client; 			// Write to client
			
		boolean logged_in;
		String server_reply;
		
		public Connection(Socket s) {
			this.client = s;
			openCommunication(s);
			
			running = true;
			
			this.logged_in = false;
			this.server_reply = null;
			
			this.username = null;
		}
		
		// This method runs in a separate thread
		public void run() {
			String line = null; 	// Client's input
			
			try {				
				while (!logged_in) { // Client is not logged in
					if (from_client.ready()) {
						line = from_client.readLine();
						
						if (line != null) {
							if (isLoggedIn(line)){
								if (running == false) { // User name is already in user
									writeToClient(this.command2());
									return;
								}
								synchronized (ChatServer.allSockets) { // Make sure only one thread can modify list of users	
									this.writeToAll(this.command10()); // Announces new connection to everyone
									
									ChatServer.allSockets.add(this); // Add new connection to list of all connection
									server_reply = this.command1(); // Acceptance for user name
									this.writeToClient(server_reply);
								}
							}
							else {
								server_reply = "Please log in!";
								this.writeToClient(server_reply);
							}
						}
					}
				}
					
				// Get to here means client is logged in
				while (running) {
					if (from_client.ready()) {
						line = from_client.readLine();
						
						if ((line != null) && (line.length() >= 2)) {
							if (Character.isDigit(line.charAt(0)) && (line.charAt(1) == ' ')) {
								switch (line.charAt(0)) {
									case '3': // General message to all clients
										server_reply = this.command5(line);
										writeToAll(server_reply);
										break;
									case '4': // Send a private message from client to client
										String[] temp = this.command6(line);
										server_reply = temp[1]; // The private message
										
										for (Connection c: ChatServer.allSockets) {
											// Find the client that the message will go to
											if (c.username.equals(temp[0])) {
												c.writeToClient(server_reply);
											}
										}
										
										this.writeToClient("Private message sent!"); // Notify this client
										break;
									case '7': // Client disconnects
										running = false; // This will stop this thread
										
										server_reply = this.command8(); // Says Goodbye! to disconnect request
										this.writeToClient(server_reply); 
										
										ChatServer.allSockets.remove(this);
										
										server_reply = this.command9(); // Announce a disconnection
										this.writeToAll(server_reply);
										
										break;
								}
								
							}
						}
					}
				}				 
			} catch (IOException ioe) {} 
			finally { //close all the streams
				if (from_client != null)
					try {
						from_client.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				if (to_client != null)
					to_client.close();
				
				if (ChatServer.allSockets.contains(this)) {
					ChatServer.allSockets.remove(this); // Remove disconnected client off the list
				}
			}
		}
		
		// Initialize input and output streams for this connection
		private void openCommunication(Socket client) {
			try {
				from_client = new BufferedReader(new InputStreamReader(client.getInputStream()));	//read input from client
				to_client = new PrintWriter(client.getOutputStream());	//write to output to client
			} catch (IOException e) {}
		}
		
		// Write to client's socket
		private void writeToClient(String message) {
			to_client.println(message);
			to_client.flush();
		}
		
		// Write to all sockets
		private void writeToAll(String message) {
			ArrayList<Connection> all = ChatServer.allSockets;
			
			for (Connection c: all) {
				c.writeToClient(message);
			}
		}
		
		// Check if this connection has logged in
		private boolean isLoggedIn(String command) {
			int min_length = 3; // command to log in has minimum length
			
			if ((command != null) && (command.length() > min_length)){
				if ((command.charAt(0) == '0') && (command.charAt(1) == ' ')) {
					logged_in = true;
					this.username = (command.substring(2)).toLowerCase(); // User name is ignored-case
					// User name can only contains word characters
					// \W means anything other than letters, numbers, and underscores
					this.username = this.username.replaceAll("\\W", "");

					// User name has minimum length
					if (this.username.length() > ChatRoomRules.USERNAME_LENGTH) {
						this.username = this.username.substring(0, ChatRoomRules.USERNAME_LENGTH);
					}

					for (Connection c: ChatServer.allSockets) {
						if (c.username.equals(this.username)) {
							running = false;
						}
					}

					return true;
				}
			}
			return false;
		}
		
		// Get current time in GMT
		private String getCurrentGMT() {
			final SimpleDateFormat sdf = new SimpleDateFormat("YYYY:MM:dd:HH:mm:ss");
		    sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		    final String utcTime = sdf.format(new Date(System.currentTimeMillis()));

		    return utcTime;
		}
		
		/*
		 * Server accepts username
		 * This is command 1 - <1><” “><user1,user2,user3,...><” “><welcome message></r/n>
		 */
		private String command1() {
			String reply = "1 ";
			
			for (Connection c: ChatServer.allSockets) {
				reply = reply + c.username + ",";
			}
			int last_comma = reply.lastIndexOf(','); // Last comma on the list of online user
			if (last_comma != -1) { // There is at least one user online
				reply = reply.substring(0, last_comma); // get rid of the last , on the last user
			}
			
			reply = reply + " Welcome to the chatroom, " + this.username;
			
			return reply;
		}
		
		// Message when server denies user name
		private String command2() {
			String reply = "2 \r\n";
			
			return reply;
		}
		
		
		/* 
		 * Server sends general message to user
		 * This is command 5 - <5><” “><Username><” “><time><” “><message></r/n>
		 * In reply to command 3 from client - <3><” “><message></r/n>
		 */
		private String command5(String message) {
			String line;
			
			int space = message.indexOf(" ");
			
			line = "5 " + this.username + " " + this.getCurrentGMT() + " " + message.substring(space+1) + "\r\n";
			
			return line;
		}
		
		
		/**
		 * Server sends general message to user 
		 * This is command 6 - <6><” “><fromUsername><” “><toUsername><” “><time><” “><message></r/n>
		 * In reply to command 4(user sends general message to server)
		 * <4><” “><fromUsername><” “><toUsername><” “><message></r/n>
		 * 
		 * @param message - the message that client sent to server (aka command 4)
		 * 
		 * @return String[] - Array of 2 String with 
		 * 		[0] What user to send to
		 * 		[1] What the message is 
		 * 
		 */
		private String[] command6(String message) {
			String line = message;
			String from_user = null;
			String to_user = null;
			String reply = null;
			
			int space = 0;
			
			space = line.indexOf(" ");
			
			line = line.substring(space+1);
			space = line.indexOf(" ");
			from_user = line.substring(0, space);
			
			line = line.substring(space+1);
			space = line.indexOf(" ");
			to_user = line.substring(0, space);
			
			line = line.substring(space+1);
			
			reply = ("6 " + from_user + " " + to_user + " " + 
					this.getCurrentGMT() + " " + line + "\r\n");
			
			return (new String[] {to_user, reply});
		}
		
		/**
		 * Server says Goodbye! to disconnect request 
		 * This is command 8 - <8></r/n>
		 * In reply to command 7 (Client sends a disconnect request)
		 * <7></r/n>
		 * 
		 * @return String - the protocol to send back to client
		 * 
		 */
		private String command8() {
			return "8 \r\n";
		}
		
		
		/**
		 * Server announces client user has disconnected
		 * This is command 9 - <9><” “><username></r/n>
		 * In reply to command 7 (Client sends a disconnect request)
		 * <7></r/n>
		 * 
		 * @return String - the protocol to announce a leaving
		 * 
		 */
		private String command9() {
			return "9 " + this.username + "\r\n";
		}
		
		
		/**
		 * Server announces a client user has connected
		 * This is command 10 - <10><” “><username></r/n>
		 * Sends along with command 1 (Server accepts user name)
		 * 
		 * @return String - the protocol to announce a new connection
		 * 
		 */
		private String command10() {
			return "10 " + this.username + "\r\n";
		}
		
	}
}