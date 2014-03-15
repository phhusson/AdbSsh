import android.Keys._

android.Plugin.androidBuild

name := "AdbSsh"

scalaVersion := "2.10.3"

proguardOptions in Android ++= Seq("-dontobfuscate",
  "-dontoptimize",
  "-dontwarn com.jcraft.**",
  "-dontwarn org.ietf.jgss.**",
  "-keepclasseswithmember class com.jcraft.jsch.**",
  "-keepclasseswithmember class me.phh.**")

libraryDependencies += "org.scaloid" %% "scaloid" % "3.0-8"

libraryDependencies += "org.scalaj" %% "scalaj-http" % "0.3.12"

scalacOptions in Compile += "-feature"

run <<= run in Android

install <<= install in Android
