/**
 *
 * The controller portion of the MVC pattern.
 */

import java.awt.event.ActionEvent;import java.awt.event.ActionListener;import java.awt.event.KeyEvent;import java.awt.event.KeyListener;import java.beans.PropertyChangeEvent;import java.beans.PropertyChangeListener;

public class ChatController implements PropertyChangeListener {
	private ChatView view;
	private ChatModel model;	    /**     * properties constants called by the GUI event listeners of the view.     */    public static final String CONVERSATION_PROPERTY = "conversation";        public ChatController() {    	view = new ChatView();		model = new ChatModel();		model.addPropertyChangeListener(this);    }
	public static void main(String[] args) {		ChatController controller = new ChatController();
		
		controller.view.setSendListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				final String message1 = controller.view.getSendText();				controller.model.setClientMessage(message1);				controller.model.sendToServer();
			}
		});		
		controller.view.setExitListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {				controller.model.setClientMessage("/o"); // Client clicks Exit so send a disconnect request				controller.model.sendToServer();
				System.exit(0);
			}
		});
		controller.view.setSendKeyListener(new KeyListener() {
			public void keyTyped(KeyEvent arg0) {
				/** not implemented */
			}
			public void keyPressed(KeyEvent arg0) {
	            if (arg0.getKeyCode() == KeyEvent.VK_ENTER) {	            	final String message1 = controller.view.getSendText();					controller.model.setClientMessage(message1);					controller.model.sendToServer();
	            }
			}
			public void keyReleased(KeyEvent arg0) {
				/** not implemented */
			}
		});
	}	@Override	public void propertyChange(PropertyChangeEvent event) {		view.modelPropertyChange(event);	}	
}
