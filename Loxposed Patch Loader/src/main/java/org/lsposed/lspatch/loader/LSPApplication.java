package org.lsposed.lspatch.loader;
import nea.lox.patchloader.*;
import java.io.*;
import android.os.*;

public class LSPApplication
{
	public static void onLoad ()
	{
		try
		{
			LoxApplication.load();
		}
		catch (Throwable e)
		{
			/*try
			{
				FileWriter out = new FileWriter(new File(Environment.getExternalStorageDirectory(), "Loxposed/error.log"));
				e.printStackTrace(new PrintWriter(out));
				out.flush();
				out.close();
			}
			catch (IOException e2)
			{}*/
		}
	}
}
