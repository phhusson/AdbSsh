package me.phh.AdbSsh

import org.scaloid.common._
import android.graphics.Color
import android.app.PendingIntent
import android.content.{Intent,Context}
import android.hardware.usb.{UsbManager,UsbDevice,UsbInterface,UsbDeviceConnection}
import android.hardware.usb.{UsbEndpoint,UsbConstants}
import com.cgutman.adblib._
//import com.cgutman.adblib.AdbProtocol.AdbMessage
import java.io.InputStream
import java.nio.{ByteBuffer,ByteOrder}
import android.util.Base64

import scala.concurrent.ops._
import scala.collection.mutable.SynchronizedQueue

class AdbMessage {
	var command : Int = -1;
	var arg0 : Int = -1;
	var arg1 : Int = -1;
	var payloadLength : Int = -1;
	var checksum : Int = -1;
	var magic : Int = -1;
	var payload : Array[Byte] = null;
};

class AdbService extends LocalService {
	var adbDevice: UsbDevice = null;
	var adbInterface: UsbInterface = null;
	var connection: UsbDeviceConnection = null;

	var inEP: UsbEndpoint = null;
	var outEP: UsbEndpoint = null;

	//Forced size, bad...
	val remoteIds = Array.fill[Integer](256)(0);
	val queues = Array.fill[SynchronizedQueue[Char]](256) { new SynchronizedQueue[Char] };

	var currentId = 0;

	def connect(adbDevice: UsbDevice, adbInterface: UsbInterface): Unit = {
		this.adbDevice = adbDevice;
		this.adbInterface = adbInterface;

		warn("Got access to adb !");
		connection = usbManager.openDevice(adbDevice);
		connection.claimInterface(adbInterface, false);

		var i = 0;
		for(i <- 0 until adbInterface.getEndpointCount) {
			val EP = adbInterface.getEndpoint(i);
			if(EP.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
				if(EP.getDirection() == UsbConstants.USB_DIR_IN) {
					inEP = EP;
				} else {
					outEP = EP;
				}
			}
		}

		adbConnect

		spawn {
			val buf = new Array[Byte](4096);
			var sentSignature : Boolean = false;
			val crypto = AdbCrypto.generateAdbKeyPair(getBase64Impl);

			var connected : Boolean = false;
			var maxDataSize = 0;
			var remoteId = 1;
			var step = 0;
			while (true) {
				val msg : AdbMessage = read_msg();
				warn("Got command = " + msg.command);

				msg.command match {
					case AdbProtocol.CMD_AUTH => {
						warn("Got a AUTH command");
						if(msg.arg0 == AdbProtocol.AUTH_TYPE_TOKEN) {
							if(sentSignature) {
								val answer = AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_RSA_PUBLIC,
								crypto.getAdbPublicKeyPayload());
								write_msg(answer);
								warn("Send Public Key");
							} else {
								val answer = AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_SIGNATURE, crypto.signAdbTokenPayload(msg.payload));
								write_msg(answer);
								sentSignature = true;
							}
						}
					}
					case AdbProtocol.CMD_CNXN => {
						warn("Got a CNXN command");
						connected = true;
						maxDataSize = msg.arg1;
					}
					case AdbProtocol.CMD_OKAY => {
						warn("Got a OKAY command");
						val remoteId = msg.arg0
						val localId = msg.arg1
						warn("Got remote id = " + remoteId + " for " + localId);
						if(msg.arg1 != currentId) {
							error("Wrong channel...");
						}
						remoteIds.synchronized {
							remoteIds(localId) = remoteId;
							warn("Now remoteId = " + remoteIds(localId));
							remoteIds.notifyAll();
						}
					}
					case AdbProtocol.CMD_WRTE => {
						warn("Got a WRTE command");
						val str = new String(msg.payload, "ASCII");
						warn(" localId = " + msg.arg1);
						warn(" remoteId = " + msg.arg0);
						warn(" remoteId2 = " + remoteIds(msg.arg1));
						warn("Str = " + str);
						var i = 0;
						queues(msg.arg1).synchronized {
							for(i <- 0 until msg.payload.length)
								queues(msg.arg1).enqueue(msg.payload(i).toChar);
							queues(msg.arg1).notifyAll();
						}
						adbReady(msg.arg1, msg.arg0);
					}
					case AdbProtocol.CMD_CLSE => {
						warn("Channel closed");
						remoteIds.synchronized {
							remoteIds(msg.arg1) = -1;
							remoteIds.notifyAll();
						}
					}
					case _ => {
						warn("Got an unknown command");
					}
				}
			}
		}
	}

	def open(chan: String): Integer = {
		try {
			currentId = currentId + 1;
			val id = currentId;
			write_msg( AdbProtocol.generateOpen(id, chan));
			remoteIds.synchronized {
				while(remoteIds(id) == 0) {
					remoteIds.wait();
				}
				return id;
			}
		} catch {
			case e: Exception => error("Got exception on " + e);
		}
		return -1;
	}

	def write(id: Int, msg: String) = {
		warn("Writing to " + remoteIds(id));
		adbWrite(id, remoteIds(id), msg)
	}

	def read(id: Int): String = {
		remoteIds.synchronized {
			while(remoteIds(id) <= 0) {
				remoteIds.wait();
			}
		}
		queues(id).synchronized {
			while(queues(id).isEmpty)
				queues(id).wait();
		}

		val ret = queues(id).dequeueAll(s => true);
		return ret.mkString
	}

	implicit val tag = new LoggerTag("UsbHost:ADB");
	@inline def usbManager(implicit context: Context): UsbManager = context.getSystemService(Context.USB_SERVICE).asInstanceOf[UsbManager]

	// This implements the AdbBase64 interface required for AdbCrypto
	def getBase64Impl : AdbBase64 = {
		return new AdbBase64() {
			override def encodeToString(arg0: Array[Byte]): String = {
				return Base64.encodeToString(arg0, Base64.DEFAULT);
			}
		}
	}

	def write_msg(msg: Array[Byte]) = {
		val n_bytes = connection.bulkTransfer(outEP, msg, 24, 1000); 
		warn("Write " + n_bytes);

		if( (msg.length - 24) > 0) {
			val n_bytes2 = connection.bulkTransfer(outEP, msg.drop(24), msg.length-24, 1000); 
			warn("Write2 " + n_bytes2);
		}
	}

	def read_msg() : AdbMessage= {
		val hdr = new Array[Byte](24);
		val n_bytes_hdr = connection.bulkTransfer(inEP, hdr, 24, 100000000);
		if(n_bytes_hdr != 24) {
			error("Meeeeeeeeeeeeeeeeeeeeeeeeeeeh");
			return null;
		}

		var packet = ByteBuffer.wrap(hdr);
		packet.order(ByteOrder.LITTLE_ENDIAN);

		val ret : AdbMessage = new AdbMessage;
		ret.command = packet.getInt();
		ret.arg0 = packet.getInt();
		ret.arg1 = packet.getInt();
		ret.payloadLength = packet.getInt();
		ret.checksum = packet.getInt();
		ret.magic = packet.getInt();

		if (ret.payloadLength != 0) {
			var tot = 0;
			ret.payload = new Array[Byte](ret.payloadLength);
			while(tot < ret.payloadLength) {
				val tmp = new Array[Byte](ret.payloadLength - tot);
				val res = connection.bulkTransfer(inEP, tmp, ret.payloadLength-tot, 1000);
				tmp.copyToArray(ret.payload, tot, res);
				if(res >= 0) {
					tot = tot + res;
				}
			}
		}
		return ret;
	}

	def adbWrite(localId: Integer, remoteId: Integer, cmd: String) = {
		val msg = AdbProtocol.generateWrite(1, remoteId, cmd.getBytes);
		write_msg(msg)
	}

	def adbReady(localId: Integer, remoteId: Integer) = {
		val msg = AdbProtocol.generateReady(localId, remoteId)
		write_msg(msg)
	}

	def adbConnect() = {
		write_msg(AdbProtocol.generateConnect)
	}
}
