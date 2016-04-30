import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Xuan Mai
 * @since 04/11/2016
 *
 * Client's socket that will write to server
 * and read server's message
 * 
 */
public class SocketReader implements Runnable {
	public static final int PORT = 1337;
	public static final String HOST = "127.0.0.1";
	
	ChatModel model = null;
	String username = null;
	
	ArrayList<String> user_list;
	
	BufferedReader from_server = null; // the reader from the network
	PrintWriter to_server = null;
	Socket socket = null;   // the socket
	
	boolean send_now = false;
	
	public SocketReader(ChatModel m) {
		this.model = m;
		this.user_list = new ArrayList<String>();
	}
	
	// This method runs in a separate thread
	public void run() {
		String server_reply;
		String client_message_mode;

		try {
			socket = new Socket(HOST, PORT); //create a socket that listens to port 1337
			// a reader to read from socket
			from_server = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			to_server = new PrintWriter(socket.getOutputStream(), true);
			
			while (true) {
				if (sendMessage()) { // There is a message to send
					client_message_mode = this.messageStartWith(model.getClientMessage());
					
					switch (client_message_mode) {
						case "login":  // Send command 0 (request user name) to server
							model.setClientMessage(this.constructCommand0(model.getClientMessage()));
							break;
						case "logout": // Send command 7 (request disconnection) to server
							model.setClientMessage(this.constructCommand7());
							break;
						case "whisper":
							model.setClientMessage(this.constructCommand4(model.getClientMessage()));
							break;
						case "all":
							model.setClientMessage(this.constructCommand3(model.getClientMessage()));
							break;
					}
					
					
					to_server.println(model.getClientMessage());
					this.setSendMessage(false); // Sent client's message, no more message to send
				}
				
				if (from_server.ready()) {
					server_reply = from_server.readLine();
					
					if (server_reply.length() <= ChatRoomRules.MESSAGE_LENGTH) {
						if ((server_reply != null) && (server_reply.length() >= 2)) {
							if (Character.isDigit(server_reply.charAt(0)) && (server_reply.charAt(1) == ' ')) {
								switch (server_reply.charAt(0)) {
								case '1': // Got a welcome message from server
									server_reply = this.command1(server_reply);
									break;
								case '2': // Got a denial for user name from server
									server_reply = this.command2();
									break;
								case '5': // Got a general message from server
									server_reply = this.command5(server_reply);
									break;
								case '6': // Got a private message
									server_reply = this.command6(server_reply);
									break;
								case '8': 
									server_reply = this.command8();
									break;
								case '9':
									server_reply = this.command9(server_reply);
									break;
								}
							}
							else if ((server_reply.length() >= 3) && (server_reply.substring(0, 3).equals("10 "))) {
								server_reply = this.command10(server_reply);
							}

							model.setServerMessage(server_reply);
							model.getServerMessage();
						}
					}
				}
				
			}
		}
		catch (IOException ioe) {
			System.err.println(ioe);
		}
		finally { //close all streams
			if (from_server != null)
				try {
					from_server.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			if (socket != null)
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}
	

	public void setSendMessage(boolean b) {
		this.send_now = b;
	}
	
	private boolean sendMessage() {
		return this.send_now;
	}
	
	/**
	 * 
	 * This return what type of message client sent
	 * 
	 * @param message - client's message
	 * 
	 * @return type of message
	 */
	private String messageStartWith(String message) {		
		if (message.length() == 2) {
			if (message.substring(0, 2).equalsIgnoreCase("/o")) {
				return "logout";
			}
		}
		else if (message.length() > 2) {
			if (message.substring(0, 3).equalsIgnoreCase("/i ")) {
				return "login";
			}
			else if (message.substring(0, 3).equalsIgnoreCase("/w ")) {
				return "whisper";
			}
			else if (message.substring(0, 3).equalsIgnoreCase("/a ")) {
				return "all";
			}
		}
		return message;
	}
	
	/**
	 * Helper method to print user list to ChatView
	 * 
	 * @return a list of users
	 */
	private String printUserList() {
		String list = "";
		
		for (String user: this.user_list) {
			list = list + user + ", ";
		}
		
		list = list.substring(0, list.length()-2); // Get rid of the last <,><" ">
		
		return list;
	}
	
	/**
	 * 
	 * This will construct client's command 0 (Client requests a username)
	 * <0><” “><username></r/n>
	 * 
	 * @param message - the message that client submitted, in format of </i><" "><username>
	 * 
	 * @return String - command 0 to send to server
	 *  
	 */
	private String constructCommand0(String message) {
		return "0" + message.substring(message.indexOf(" ")) + "\r\n";
	}
	
	
	/**
	 * 
	 * This will construct client's command 3 (User sends general message to server)
	 * <3><” “><message></r/n>
	 * 
	 * @param message - the message that client submitted, in format of </a><" "><message>
	 * 
	 * @return String - command 3 to send to server
	 *  
	 */
	private String constructCommand3(String message) {
		if (message.substring(message.indexOf(" ")).length() >= 2) { // Client's message is not empty
			return "3" + " " + message.substring(message.indexOf(" ")+1) + "\r\n";
		}
		else { // Client's message is empty
			return "3" + " " + message.substring(message.indexOf(" ")) + "\r\n";
		}
	}
	
	
	/**
	 * 
	 * This will construct client's command 4 (User sends private message to server)
	 * <4><” “><fromUsername><” “><toUsername><” “><message></r/n>
	 * 
	 * @param message - the message that client submitted, in format of </w><" "><to user><" "><message>
	 * 
	 * @return String - command 4 to send to server
	 *  
	 */
	private String constructCommand4(String message) {
		String to_user;
		String line;
		
		line = message.substring(message.indexOf(" ")+1); // line = <to user><" "><message>
		
		if (line.indexOf(" ") != -1) {
			to_user = line.substring(0, line.indexOf(" "));
			line = line.substring(line.indexOf(" ")); // line = <" "><message>
			
			if (line.length() >= 2) { // line contains more than just 1 space
				line = line.substring(1); // First index of message to the end
			}
			
			return "4 " + this.username + " " + to_user + " " + line + "\r\n";
		}
		
		return "4 " + this.username + " " + " " + " " + line + "\r\n"; // Send to a empty user
	}
	
	
	/**
	 * 
	 * This will construct client's command 7 (Client sends a disconnect request)
	 * <7></r/n>
	 * 
	 * @return String - command 7 to send to server
	 *  
	 */
	private String constructCommand7() {
		return "7 \r\n";
	}
	
	
	/**
	 * 
	 * This will parse server's command 1 (Server accepts user name)
	 * <1><” “><user1,user2,user3,...><” “><welcome message></r/n>
	 * 
	 * @param server_message - the message that server sent
	 * 
	 * @return String - a parse message for display to ChatView
	 *  
	 */
	private String command1(String server_message) {
		String line = server_message;
		String users_list;
		String message;
		
		int space = 0;
		int end_command = 0;
		
		space = line.indexOf(" ");
		line = line.substring(space+1);
		
		space = line.indexOf(" ");
		users_list = line.substring(0, space);
		
		if (users_list.lastIndexOf(',') != -1) { // More than one user in the list
			this.username = users_list.substring(users_list.lastIndexOf(',')+1); // The last user in the user list is this socket
		}
		else { // This connect is the first user
			this.username = users_list;
		}
		
		this.user_list = new ArrayList<String>(Arrays.asList(users_list.split(",")));
		
		line = line.substring(space+1);
		
		message = (line.substring(end_command) + "\n" + "All users: " + this.printUserList());
		
		return message;
	}
	
	
	/**
	 * This takes server's command 2 and
	 * return a message
	 * 
	 * @return String - a message for display to ChatView
	 * 
	 */
	private String command2() {
		return "User name is already in use. Close connection now!";
	}
	
	
	/**
	 * This will parse server's command 5 (Server sends general message to user)
	 * <5><” “><Username><” “><time><” “><message></r/n>
	 * 
	 * @param server_message - the message that server sent
	 * 
	 * @return String - a parse message for display to ChatView
	 * 
	 */
	public String command5(String server_message) {
		String line = server_message;
		String user;
		String time;
		String message;
		
		int space = 0;
		
		space = line.indexOf(" ");
		line = line.substring(space+1);
		
		space = line.indexOf(" ");
		user = line.substring(0, space);
		line = line.substring(space+1);
		
		space = line.indexOf(" ");
		time = line.substring(0, space);
		line = line.substring(space+1);
		
		message = "[" + time + "][GMT] " + user + ": " + line;
		
		return message;
	}
	
	/**
	 * This will parse server's command 6 (Server sends private message to user)
	 * <6><” “><fromUsername><” “><toUsername><” “><time><” “><message></r/n>
	 * 
	 * @param server_message - the message that server sent
	 * 
	 * @return String - a parse message for display to ChatView
	 * 
	 */
	private String command6(String from_server) {
		String line = from_server;
		String message = null;
		String from_user = null;
		String reply = null;
		String time = null;
		
		int space = 0;
		
		space = line.indexOf(" ");
		line = line.substring(space+1);
		
		space = line.indexOf(" ");
		from_user = line.substring(0, space);
		line = line.substring(space+1);
		
		space = line.indexOf(" ");
		line = line.substring(space+1);
		
		space = line.indexOf(" ");
		time = line.substring(0, space);
		
		message = line.substring(space+1);
		
		reply = "[" + time + "][GMT] Private message from " + from_user + ":\n" + message;
		
		return reply;
	}
	
	/**
	 * This takes server's command 8 and
	 * return a message
	 * 
	 * @return String - a message for display to ChatView
	 * 
	 */
	private String command8() {
		return "You have been disconnected from the chatroom!";
	}
	
	
	/**
	 * This will parse server's command 9 (Server announces a client user has disconnected)
	 * <9><” “><username></r/n>
	 * 
	 * @return String - a message for display to ChatView
	 * 
	 */
	private String command9(String message) {
		String user = message.substring(message.indexOf(" ")+1);
		
		this.user_list.remove(user);
		
		return (user + " has been disconnected from the chatroom!\n" + 
				"All users: " + this.printUserList());
	}
	
	
	/**
	 * This will parse server's command 9 (Server announces a client user has connected)
	 * <10><” “><username></r/n>
	 * 
	 * @return String - a message for display to ChatView
	 * 
	 */
	private String command10(String message) {
		String user = message.substring(message.indexOf(" ")+1);
		
		this.user_list.add(user);
		
		return (user + " has been connected from the chatroom!\n" + 
				"All users: " + this.printUserList());
	}
}
