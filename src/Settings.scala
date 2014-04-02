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
import java.io.File
import java.nio.{ByteBuffer,ByteOrder}
import android.util.Base64

import scala.concurrent.ops._
import scala.collection.mutable.SynchronizedQueue
import com.jcraft.jsch
import com.jcraft.jsch.JSch

import com.google.ase.Exec

class Settings extends SActivity {
	implicit val tag = new LoggerTag("UsbHost");
	val sshService = new LocalServiceConnection[SshService];
	lazy val prefs = Prefs()

    def onButtonClickedConnected(buttonV: android.view.View) {
      sshService(_.close);
      var button: SButton = null
      buttonV match {
        case s: SButton => button = s;
        case _ => button = null
      }
      button setText "Local Shell"
      button onClick onButtonClickedDisconnected _
    }

    def onButtonClickedDisconnected(buttonV: android.view.View) {
      var button: SButton = null
      buttonV match {
        case s: SButton => button = s;
        case _ => button = null
      }
      button enabled false
      button setText "Connecting"
      var exec = new Exec(assets.open("jni/libcom_google_ase_Exec.so"), new File(cacheDir, "libcom_google_ase_Exec.so"))
      var args = new Array[Object](1);
      args(0) = exec
      spawn {
        sshService({ s =>
          try {
            s.connect(prefs.server, prefs.user, prefs.password);
            s.forwardPort(prefs.rport.toInt, classOf[ShellDaemon].getName(), args);
            runOnUiThread({
              button setText "Connected (click to disconnect)"
              button enabled true
              button.onClick(onButtonClickedConnected _)
            })
          } catch {
            case _: Throwable =>
              runOnUiThread({
                button setText "Connection failed. (click to retry)"
                button enabled true
              })
          }
        });
      }
    }

	onCreate {
		contentView = new SScrollView {
            this += new SVerticalLayout {
                STextView("SSH Server:")
                val server = SEditText(prefs.server) inputType TEXT_URI

                STextView("User:")
                val user = SEditText(prefs.user) inputType TEXT_URI

                STextView("Password:")
                val password = SEditText(prefs.password) inputType TEXT_URI

                STextView("Listening port:")
                val rport = SEditText(prefs.rport) inputType TEXT_URI

                SButton("Enregistrer").onClick({
                    prefs.server = server.text.toString
                    prefs.user = user.text.toString
                    prefs.password = password.text.toString
                    prefs.rport = rport.text.toString
                    ()
                })

                SButton("Local Shell").onClick(onButtonClickedDisconnected _)
            } padding 20.dip
        }
	}
}
