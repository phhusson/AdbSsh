package me.phh.AdbSsh

import org.scaloid.common._
import scala.concurrent.ops._

import com.jcraft.jsch.ForwardedTCPIPDaemon
import com.jcraft.jsch.ChannelForwardedTCPIP
import java.io.{InputStream,OutputStream}

class ShellDaemon extends ForwardedTCPIPDaemon {
	implicit val tag = new LoggerTag("UsbHost:Daemon");

	var is : InputStream = null;
	var os : OutputStream = null;

	def run() = {
		warn("Starting new process");
		val process = new ProcessBuilder("/system/bin/sh", "-i").redirectErrorStream(true).start();
		val os_shell = process.getOutputStream
		val is_shell = process.getInputStream
		spawn {
			var res = 0
			while(res != -1) {
				var res = is_shell.read
				os.write(res);
			}
		}

		var res = 0
		while(res != -1) {
			res = is.read
			if(res != '\r')
				os_shell.write(res)
		}
	}

	def setArg(args: Array[Object]): Unit = {
	}

	def setChannel(channel: ChannelForwardedTCPIP, in: InputStream, out: OutputStream): Unit = {
		warn("Got setChannel !");
		is = in;
		os = out;
	}
}
