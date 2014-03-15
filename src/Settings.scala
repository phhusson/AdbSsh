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

class Settings extends SActivity {
  implicit val tag = new LoggerTag("UsbHost");

  onCreate {
    val prefs = Prefs()
    contentView = new SVerticalLayout {
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
    } padding 20.dip
  }
}
