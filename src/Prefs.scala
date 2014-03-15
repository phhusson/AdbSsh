package me.phh.AdbSsh

import org.scaloid.common._
import android.content.{SharedPreferences,Context}

class Prefs(preferences : SharedPreferences) extends Preferences(preferences) {
	def server = super.server("toto.phh.me");
	def server_=(s: String):Unit = super.server = s

	def user = super.user("toto");
	def user_=(s: String):Unit = super.user = s

	def password = super.password("toto");
	def password_=(s: String):Unit = super.password = s

	def rport = super.rport("4000");
	def rport_=(s: String):Unit = super.rport = s
}

object Prefs {
	def apply()(implicit ctx: Context) = new Prefs(defaultSharedPreferences);
}
