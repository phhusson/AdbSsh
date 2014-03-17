package me.phh.AdbSsh

import org.scaloid.common._
import android.graphics.Color
import android.app.PendingIntent
import android.content.{Intent,Context}
import android.hardware.usb.{UsbManager,UsbDevice,UsbInterface,UsbDeviceConnection}
import android.hardware.usb.{UsbEndpoint,UsbConstants}
import com.cgutman.adblib._
import java.io.InputStream
import java.io.File
import java.nio.{ByteBuffer,ByteOrder}
import android.util.Base64

import scala.concurrent.ops._
import scala.collection.mutable.SynchronizedQueue
import com.jcraft.jsch
import com.jcraft.jsch.JSch

class Main extends SActivity {
	implicit val tag = new LoggerTag("UsbHost");
	@inline def usbManager(implicit context: Context): UsbManager = context.getSystemService(Context.USB_SERVICE).asInstanceOf[UsbManager]
	val adbService = new LocalServiceConnection[AdbService];
	val sshService = new LocalServiceConnection[SshService];

	def main(adbDevice: UsbDevice, adbInterface: UsbInterface, session: jsch.Session) = {
		warn("Hello !")
		adbService({ adb: AdbService =>
			adb.connect(adbDevice, adbInterface);
		})
		adbService.onConnected += { adb: AdbService =>
			adb.connect(adbDevice, adbInterface);
		}
	}

	// This implements the AdbBase64 interface required for AdbCrypto
	def getBase64Impl : AdbBase64 = {
		return new AdbBase64() {
			override def encodeToString(arg0: Array[Byte]): String = {
				return Base64.encodeToString(arg0, Base64.DEFAULT);
			}
		}
	}


	def setupCrypto() : AdbCrypto = {
		val pub : File = new File("pub.key")
		val priv: File = new File("pub.key")
		if(pub.exists && priv.exists) {
			return AdbCrypto.loadAdbKeyPair(getBase64Impl, priv, pub);
		}

		val crypto = AdbCrypto.generateAdbKeyPair(getBase64Impl);
		crypto.saveAdbKeyPair(priv, pub)

		return crypto;
	}

	onCreate {
		val prefs = Prefs()
		contentView = new SVerticalLayout {
			STextView("Hello !");
		} padding 20.dip

		val deviceList = usbManager.getDeviceList();
		var deviceIterator = deviceList.values().iterator();
		var adbDevice : UsbDevice = null;
		var adbInterface : UsbInterface = null;

		while(deviceIterator.hasNext()) {
			val device = deviceIterator.next()
			warn("Device = " + device.getDeviceName);
			var a = 0;
			for(a <- 0 until device.getInterfaceCount) {
				val interface = device.getInterface(a);
				warn("  Interface :");
				warn("    Class = " + interface.getInterfaceClass);
				warn("    Protocol = " + interface.getInterfaceProtocol);
				warn("    Subclass = " + interface.getInterfaceSubclass);
				if(interface.getInterfaceClass == 255 &&
					interface.getInterfaceProtocol == 1 &&
					interface.getInterfaceSubclass == 66) {
						//ADB interface
						adbDevice = device;
						adbInterface = interface;
				}
			}
		}

		val mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("me.phh.UsbHost.Usb_Permission"), 0);
		var session: jsch.Session = null;

		broadcastReceiver("me.phh.UsbHost.Usb_Permission") { (context, intent) =>
			val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE).asInstanceOf[UsbDevice];
			if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) || device == null) {
				spawn {
					main(adbDevice, adbInterface, session);
				}
			}
		}

		/*
		spawn {
			try {
				warn("Hell for JSch thread");
				val ssh = new JSch();
				JSch.setLogger(new jsch.Logger() {
					def isEnabled(level: Int): Boolean = { true }
					def log(level: Int, msg: String) = {
						warn("JSCH: " + msg);
					}
				})
				val session = ssh.getSession(prefs.user, prefs.server, 22);
				warn("Got session");
				session.setPassword(prefs.password);
				warn("Set passwd");
				session.setConfig("StrictHostKeyChecking", "no");
				warn("Strict host key")
				session.connect(30000);
				warn("Connected");

				val channel = session.openChannel("shell");
				warn("Opened channel")
				channel.setInputStream(null);
				warn("Set IS")
				channel.setOutputStream(new java.io.OutputStream {
					override def write(b: Int): Unit = {
						warn("SSH: " + b.toChar);
					}
				});
				channel.connect();
				warn("Channel connected")
				session.setPortForwardingR("*", prefs.rport.toInt, classOf[AdbDaemon].getName(), Array(adbService).asInstanceOf[Array[Object]]);
				warn("Setted port forwarding !")
			} catch {
				case e: Exception => warn("Got an exception ! " + e);
			}
		}*/

		if(adbDevice != null) {
			warn("Requesting usb permission");
		if(usbManager.hasPermission(adbDevice)) {
			warn("Already has permission !");
			spawn {
				main(adbDevice, adbInterface, session);
			}
		} else
			usbManager.requestPermission(adbDevice, mPermissionIntent);
		}

		sshService({ s =>
			s.connect(prefs.server, prefs.user, prefs.password);
			s.forwardPort(prefs.rport.toInt, classOf[AdbDaemon].getName(), Array(adbService).asInstanceOf[Array[Object]]);
		});
	}
}
