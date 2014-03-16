package me.phh.AdbSsh

import org.scaloid.common._
import scala.concurrent.ops._

import com.jcraft.jsch.ForwardedTCPIPDaemon
import com.jcraft.jsch.ChannelForwardedTCPIP
import java.io.{InputStream,OutputStream}

class AdbDaemon extends ForwardedTCPIPDaemon {
	implicit val tag = new LoggerTag("UsbHost:Daemon");

	var is : InputStream = null;
	var os : OutputStream = null;
	var service : LocalServiceConnection[AdbService] = null;

	def run(adb: AdbService) = {
		warn("Started thread !")
		val adbId = adb.open("shell:");

		spawn {
			var src = scala.io.Source.fromInputStream(is)
			src.getLines().foreach({ s:String =>
				warn("Network receveid "+ s)
				adb.write(adbId, s+"\n")
			});
		}

		while(true) {
			val msg = adb.read(adbId);
			warn("Adb received " + msg);
			os.write(msg.getBytes)
		}
	}

	def run() = {
		warn("Started first part thread, service is "+ service.connected)
		service({ adb: AdbService => run(adb); });
	}

	def setArg(args: Array[Object]): Unit = {
		warn("Got arg !");
		try {
			service = args(0).asInstanceOf[LocalServiceConnection[AdbService]];
		} catch {
			case e: Exception => warn("Exception in setArg: " + e);
		}
	}

	def setChannel(channel: ChannelForwardedTCPIP, in: InputStream, out: OutputStream): Unit = {
		warn("Got setChannel !");
		is = in;
		os = out;
	}
}
