package pers.wangcw.webterm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.SynchronousQueue;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import net.sf.json.JSONObject;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

@ServerEndpoint(value = "/webterm")
public class WebTerm {

	private JSch jsch = new JSch();
	private String username = "";
	private String password = "";
	private static String host = "";
	private int port = 22;
	private String charset = "utf-8";
	private com.jcraft.jsch.Session sshsession;
	private MyUserInfo ui = null;
	private ChannelShell channel = null;
	private InputStream in = null;
	private OutputStream out = null;
	private BufferedReader br = null;
	private static final int readbuffer = 1024;
	private SynchronousQueue<String> queue = new SynchronousQueue<String>(true);
	private Session session;
	private SSHReadThread rt;
	private WSSendThread st;
	private boolean conected = false;

	/**
	 * Maps key press events to the ascii values
	 */
	static Map<Integer, byte[]> keyMap = new HashMap<Integer, byte[]>();

	static {
		// ESC
		keyMap.put(27, new byte[] { (byte) 0x1b });
		// ENTER
		keyMap.put(13, new byte[] { (byte) 0x0d });
		// LEFT
		keyMap.put(37, new byte[] { (byte) 0x1b, (byte) 0x4f, (byte) 0x44 });
		// UP
		keyMap.put(38, new byte[] { (byte) 0x1b, (byte) 0x4f, (byte) 0x41 });
		// RIGHT
		keyMap.put(39, new byte[] { (byte) 0x1b, (byte) 0x4f, (byte) 0x43 });
		// DOWN
		keyMap.put(40, new byte[] { (byte) 0x1b, (byte) 0x4f, (byte) 0x42 });
		// BS
		keyMap.put(8, new byte[] { (byte) 0x7f });
		// TAB
		keyMap.put(9, new byte[] { (byte) 0x09 });
		// CTR
		keyMap.put(17, new byte[] {});
		// DEL
		keyMap.put(46, "\033[3~".getBytes());
		// CTR-A
		keyMap.put(65, new byte[] { (byte) 0x01 });
		// CTR-B
		keyMap.put(66, new byte[] { (byte) 0x02 });
		// CTR-C
		keyMap.put(67, new byte[] { (byte) 0x03 });
		// CTR-D
		keyMap.put(68, new byte[] { (byte) 0x04 });
		// CTR-E
		keyMap.put(69, new byte[] { (byte) 0x05 });
		// CTR-F
		keyMap.put(70, new byte[] { (byte) 0x06 });
		// CTR-G
		keyMap.put(71, new byte[] { (byte) 0x07 });
		// CTR-H
		keyMap.put(72, new byte[] { (byte) 0x08 });
		// CTR-I
		keyMap.put(73, new byte[] { (byte) 0x09 });
		// CTR-J
		keyMap.put(74, new byte[] { (byte) 0x0A });
		// CTR-K
		keyMap.put(75, new byte[] { (byte) 0x0B });
		// CTR-L
		keyMap.put(76, new byte[] { (byte) 0x0C });
		// CTR-M
		keyMap.put(77, new byte[] { (byte) 0x0D });
		// CTR-N
		keyMap.put(78, new byte[] { (byte) 0x0E });
		// CTR-O
		keyMap.put(79, new byte[] { (byte) 0x0F });
		// CTR-P
		keyMap.put(80, new byte[] { (byte) 0x10 });
		// CTR-Q
		keyMap.put(81, new byte[] { (byte) 0x11 });
		// CTR-R
		keyMap.put(82, new byte[] { (byte) 0x12 });
		// CTR-S
		keyMap.put(83, new byte[] { (byte) 0x13 });
		// CTR-T
		keyMap.put(84, new byte[] { (byte) 0x14 });
		// CTR-U
		keyMap.put(85, new byte[] { (byte) 0x15 });
		// CTR-V
		// keyMap.put(86, new byte[]{(byte) 0x16});
		// CTR-W
		keyMap.put(87, new byte[] { (byte) 0x17 });
		// CTR-X
		keyMap.put(88, new byte[] { (byte) 0x18 });
		// CTR-Y
		keyMap.put(89, new byte[] { (byte) 0x19 });
		// CTR-Z
		keyMap.put(90, new byte[] { (byte) 0x1A });
		// CTR-[
		keyMap.put(219, new byte[] { (byte) 0x1B });
		// CTR-]
		keyMap.put(221, new byte[] { (byte) 0x1D });

	}

	public WebTerm() {
		rt = new SSHReadThread();
		rt.start();
		st = new WSSendThread();
		st.start();
	}

	@OnOpen
	public void start(Session session) {
		session.setMaxIdleTimeout(0);
		this.session = session;
		try {
			System.out.println("WebSocket session:" + session.getId());
			if (session.isOpen()) {
				System.out.println("Client connected.");
				// session.getBasicRemote().sendText("Welcom to webterm.");
			}
		} catch (Exception e) {
			try {
				session.close();
			} catch (IOException e1) {
			}
		}
	}

	@OnMessage
	public void echoTextMessage(Session session, String msg, boolean last) {
		if (session.isOpen()) {
			System.out.println("message:" + msg);
			// session.getBasicRemote().sendText(msg, last);
			handleRequest(msg);
		}
	}

	@OnMessage
	public void echoBinaryMessage(Session session, ByteBuffer bb, boolean last) {
		try {
			if (session.isOpen()) {
				session.getBasicRemote().sendBinary(bb, last);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@OnMessage
	public void echoPongMessage(PongMessage pm) {
		
	}

	@OnClose
	public void end(Session session) {
		System.out.println("Websocket close");
		try {
			if (session != null) {
				session.close();
				session = null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void handleRequest(String message) {
		try {
			if (isJson(message)) {
				JSONObject object = JSONObject.fromObject(message);
				if (object.containsKey("login")) {
					JSONObject logininfo = object.getJSONObject("login");
					host = (String) logininfo.getString("host");
					port = (Integer) logininfo.getInt("port");
					username = (String) logininfo.getString("user");
					password = (String) logininfo.getString("pass");
					charset = (String) logininfo.getString("charset");
					System.out.println(host);
					System.out.println(port);
					System.out.println(username);
					System.out.println(password);
					System.out.println(charset);
					login();
				} else if (object.containsKey("command")) {
					String cmd = (String) object.getString("command");
					send(cmd);
				} else if (object.containsKey("logout")) {
					logout();
				} else if (object.containsKey("KeyCode")) {
					Integer keyCode = null;
					Double keyCodeDbl = (Double) object.get("keyCode");
					if (keyCodeDbl != null) {
						keyCode = keyCodeDbl.intValue();
					}
					if (keyCode != null) {
						if (keyMap.containsKey(keyCode)) {
							send(new String(keyMap.get(keyCode)));
						}
					}
				}
			} else {
				System.out.println("非法的消息" + message);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static boolean isJson(String s) {
		if (s == null || s.equals("")) {
			return false;
		}
		try {
			JSONObject object = JSONObject.fromObject(s);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public void login() {
		System.out.println("=================login=================");
		try {
			sshsession = jsch.getSession(username, host, port);
			// Properties config = new Properties();
			// config.put("StrictHostKeyChecking", "no");
			// sshsession.setConfig(config);
			// sshsession.setTimeout(5000);s
			ui = new MyUserInfo();
			sshsession.setUserInfo(ui);
			sshsession.connect();
			channel = (ChannelShell) sshsession.openChannel("shell");
			channel.setPtyType("vt100");
			channel.connect();
			in = channel.getInputStream();
			out = channel.getOutputStream();
			br = new BufferedReader(new InputStreamReader(in, charset));
			conected = true;
			try {
				session.getBasicRemote().sendText("Login success...");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		} catch (JSchException e) {
			e.printStackTrace();
			try {
				session.getBasicRemote().sendText(e.getMessage());
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
			try {
				session.getBasicRemote().sendText(e.getMessage());
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}

	public void logout() {
		System.out.println("=================logout=================");
		conected = false;
		// if (rt != null) {
		// rt.interrupt();
		// rt = null;
		// }
		// if (st != null) {
		// st.interrupt();
		// st = null;
		// }
		if (channel != null) {
			channel.disconnect();
			channel = null;
		}
		if (sshsession != null) {
			sshsession.disconnect();
			sshsession = null;
		}
	}

	public void send(String cmd) {
		try {
			System.out.println("===============cmd==============" + cmd);
			// out.write((cmd + "\n").getBytes());
			out.write(cmd.getBytes());
			// out.write("\033[D".getBytes());
			// out.write('\n');
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public class MyUserInfo implements UserInfo, UIKeyboardInteractive {
		public String getPassword() {
			return password;
		}

		public boolean promptYesNo(String str) {
			return true;
		}

		public String getPassphrase() {
			return null;
		}

		public boolean promptPassphrase(String message) {
			return true;
		}

		public boolean promptPassword(String message) {
			return true;
		}

		public void showMessage(String message) {
			System.out.println(message);
		}

		public String[] promptKeyboardInteractive(String destination,
				String name, String instruction, String[] prompt, boolean[] echo) {
			String[] response = new String[prompt.length];
			for (int i = 0; i < prompt.length; i++) {
				response[i] = password;
			}
			return response;
		}
	}

	private class SSHReadThread extends Thread {

		public void run() {
			System.out.println("start ReadThread");
			while (!SSHReadThread.interrupted()) {
				try {
					if (conected) {
						char[] charBuf = new char[readbuffer];
						StringBuilder sb = new StringBuilder();
						int size = -1;
						if ((size = br.read(charBuf)) != -1) {
							// System.out.println("=========================="+size);
							sb.append(charBuf, 0, size);
							String result = new String(sb.toString());
							System.out.println(result);
							queue.put(result);
						}
					}
					Thread.sleep(50);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private class WSSendThread extends Thread {
		public void run() {
			System.out.println("start SendThread");
			while (!WSSendThread.interrupted()) {
				try {
					if (conected) {
						String echo = queue.take();
						if (echo != null & !echo.equals("")) {
							if (session.isOpen()) {
								session.getBasicRemote().sendText(echo);
							}
						}
					}
					Thread.sleep(50);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public static void main(String[] args) {
		System.out.println(UUID.randomUUID().toString());
	}

}
