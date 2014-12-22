<%@ page language="java" import="java.util.*" pageEncoding="UTF-8"%>
<%
	String path = request.getContextPath();
	String basePath = request.getScheme() + "://"
			+ request.getServerName() + ":" + request.getServerPort()
			+ path + "/";
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>webterm</title>
<link rel="shortcut icon" href="images/favicon.ico" />
<script src="js/jquery-1.8.3.js"></script>
<script src="js/term.js"></script>
<style>
html {
	background: #555;
}
</style>
<script>
	var ws = null;
	var term = null;
	var keys = {};
	$.ajaxSetup({
		cache : false
	});
	$(function($) {

		$("#loginbtn").bind("click", function() {
			var value = $("#loginbtn").val();
			if (value == "login") {
				login();
			}
			if (value == "logout") {
				logout();
			}
		});
		term = new Terminal({
			colors : Terminal.xtermColors,
			termName : 'xterm',
			geometry: [80, 24],
			scrollback : 1000,
			useStyle : true,
			convertEol: false,
			screenKeys : true,
			visualBell: false,
			popOnBell: false,
			debug : false,
			cursorBlink : true
		});

		term.on('data', function(data) {
			sendcommand(data);
		});

		term.on('title', function(title) {
			document.title = title;
		});

		term.open(document.body);
		//term.write('\x1b[31mWelcome to term.js!\x1b[m\r\n');
		term.focus();
		try {
			var loc = window.location, ws_uri;
			var ws_uri = "<%=basePath%>" + "webterm";
			if (loc.protocol === "https:") {
				ws_uri = ws_uri.replace("https","wss");
			} else {
				ws_uri = ws_uri.replace("http","ws");
			}
			//alert(ws_uri);
			if (window.WebSocket) {
				ws = new WebSocket(ws_uri);
			} else if ('MozWebSocket' in window) {
				ws = new MozWebSocket(ws_uri);
			} else {
				alert("Your Browser Can not support WebSocket!");
			}

			if (ws != null) {
				ws.onopen = function() {
					//alert(ws.readyState);
				};

				ws.onmessage = function(evt) {
					term.write(evt.data);
					term.focus();
					//alert(document.activeElement);
				};
				
				ws.error = function(evt) {
					//alert(evt.data);
				};

				ws.onclose = function(evt) {
					//alert(ws.readyState);
				};
			}
		} catch (err) {
			alert(err);
		}
	});

	function logininfo(host, port, user, pass, charset) {
		this.host = host;
		this.port = port;
		this.user = user;
		this.pass = pass;
		this.charset = charset;
	}

	function sendcommand(cmd) {
		ws.send(JSON.stringify({
			command : cmd
		}));
	}

	function sendlogout() {
		ws.send(JSON.stringify({
			logout : "logout"
		}));
	}

	function login() {
		var host = $('#host').val();
		var port = $('#port').val();
		var user = $('#user').val();
		var pass = $('#pass').val();
		var charset = $('#charset').val();
		var obj = new logininfo(host, port, user, pass, charset);
		ws.send(JSON.stringify({
			login : obj
		}));
		$("#loginbtn").val("logout");
		$("#loginbtn").blur();
	}

	function logout() {
		sendlogout();
		$("#loginbtn").val("login");
		term.reset();
		//ws.close();
	}
</script>
</head>
<body>
	<label style="color: white"> host: </label>
	<input type="text" id="host" value="192.168.98.42"></input>
	<label style="color: white"> port: </label>
	<input type="text" id="port" value="22"></input>
	<label style="color: white"> user: </label>
	<input type="text" id="user" value="zhwh"></input>
	<label style="color: white"> pass: </label>
	<input type="password" id="pass" value="zhwh123"></input>
	<select id="charset">
		<option value="ISO8859-1">ISO8859-1</option>
		<option value="UTF-8" selected="selected">UTF-8</option>
		<option value="GBK">GBK</option>
	</select>
	<input type="button" id="loginbtn" value="login"></input>
	<br>
	<br>
</body>
</html>
