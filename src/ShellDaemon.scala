package me.phh.AdbSsh

import org.scaloid.common._
import scala.concurrent.ops._

import com.jcraft.jsch.ForwardedTCPIPDaemon
import com.jcraft.jsch.ChannelForwardedTCPIP
import java.io.{InputStream,OutputStream}
import java.io.{FileInputStream,FileOutputStream}
import com.google.ase.Exec

class ShellDaemon extends ForwardedTCPIPDaemon {
	implicit val tag = new LoggerTag("UsbHost:Daemon");

	var is : InputStream = null;
	var os : OutputStream = null;
	var exec: Exec = null;

	def run() = {
		warn("Starting new process");
		val pids = new Array[Int](1)
		val shellFd = exec.createSubprocess("/system/bin/sh", "-", null, pids)
		val pid = pids(0);

		val is_shell = new FileInputStream(shellFd);
		val os_shell = new FileOutputStream(shellFd);
		spawn {
			var res = 0
			while(res != -1) {
				var res = is_shell.read
				os.write(res);
			}
			warn("telnet output closed");
		}

		var res = 0
		while(res != -1) {
			res = is.read
			if(res != '\r')
				os_shell.write(res)
		}
		warn("telnet input closed");
	}

	def setArg(args: Array[Object]): Unit = {
		exec = args(0).asInstanceOf[Exec]
	}

	def setChannel(channel: ChannelForwardedTCPIP, in: InputStream, out: OutputStream): Unit = {
		warn("Got setChannel !");
		is = in;
		os = out;
	}
}
