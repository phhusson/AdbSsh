package com.google.ase

import java.io.InputStream
import java.io.{File,FileDescriptor,FileOutputStream}

class Exec(is: InputStream, f: File) {
	{

		if(f.exists)
			f.delete()
		f.createNewFile()

		val fop = new FileOutputStream(f)
		var res = is.read 
		while(res != -1) {
			fop.write(res)
			res = is.read
		}
		fop.flush()
		fop.close()
		System.load(f.getAbsolutePath)
	}

	@native def createSubprocess(cmd: String, arg0: String, arg1: String, proccessId: Array[Int]): FileDescriptor
	@native def setPtyWindowSize(fd: FileDescriptor, row: Integer, col: Integer, xpixel: Integer, ypixel: Integer)
	@native def waitFor(processId: Int)
}
