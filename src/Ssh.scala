package me.phh.AdbSsh

import org.scaloid.common._
import android.graphics.Color
import android.app.PendingIntent
import android.content.{Intent,Context}
import android.hardware.usb.{UsbManager,UsbDevice,UsbInterface,UsbDeviceConnection}
import android.hardware.usb.{UsbEndpoint,UsbConstants}
import com.cgutman.adblib._
//import com.cgutman.adblib.AdbProtocol.AdbMessage
import java.io.{InputStream,OutputStream}
import java.io.File
import java.nio.{ByteBuffer,ByteOrder}
import android.util.Base64

import scala.concurrent.ops._
import scala.collection.mutable.SynchronizedQueue
import com.jcraft.jsch
import com.jcraft.jsch.JSch

class SshService extends LocalService {
	var session: jsch.Session = null;
	var ssh = new JSch();

	def connect(host: String, user: String, password: String) = {
		try {
			warn("Hell for JSch thread");
			JSch.setLogger(new jsch.Logger() {
				def isEnabled(level: Int): Boolean = { true }
				def log(level: Int, msg: String) = {
					warn("JSCH: " + msg);
				}
			})
			session = ssh.getSession(user, host, 22);
			warn("Got session");
			session.setPassword(password);
			warn("Set passwd");
			session.setConfig("StrictHostKeyChecking", "no");
			warn("Strict host key")
			session.connect(30000);
			warn("Connected");
		} catch {
			case e: Exception => warn("Got an exception ! " + e);
		}
	}

	def forwardPort(rport: Integer, handler: String, opts: Array[Object]) = {
		session.setPortForwardingR("*", rport, handler, opts);
		warn("Setted port forwarding !")
	}

	def shell(is: InputStream, os: OutputStream) = {
		val channel = session.openChannel("shell");
		warn("Opened channel")
		channel.setInputStream(is);
		warn("Set IS")
		channel.setOutputStream(os);
		warn("Set OS")
		channel.connect();
		warn("Channel connected")
	}

	def close() = {
		session.disconnect();
	}
}
