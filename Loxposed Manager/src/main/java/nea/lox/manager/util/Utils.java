package nea.lox.manager.util;

import android.os.*;
import java.io.*;
import android.content.*;
import java.util.zip.*;
import java.util.*;
import android.content.pm.*;
import android.content.pm.PackageManager.*;

public class Utils
{
	public static String LOX_DIR_NAME;
	public static String LOX_PATH;
	public static String APP_PATH;
	public static String PATCH_PATH;

	static
	{
		try
		{
			InputStream in = Utils.class.getClassLoader().getResourceAsStream("assets/loxdir.txt");
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Utils.transfer(in, out);
			in.close();
			initPaths(Environment.getExternalStorageDirectory().getPath(), LOX_DIR_NAME = new String(out.toByteArray(), "utf-8").trim());
		}
		catch (Exception e) {}
	}

	public static void deleteFile(String path)
	{
		deleteFile(new File(path));
	}

	public static void deleteFile(File file)
	{
		try
		{
			if (file.isFile())
			{
				file.delete();
			} else if (file.isDirectory())
			{
				for (File child: file.listFiles())
				{
					deleteFile(child);
				}
				file.delete();
			}
		}
		catch (Exception e)
		{}
	}

	public static File newFolder(String path)
	{
		File file = new File(path);
		if (file.isFile())
		{
			file.delete();
		}
		file.mkdirs();
		return file;
	}

	public static File newFile(String path)
	{
		File file = new File(path);
		if (file.isDirectory())
		{
			deleteFile(file);
		}
		file.getParentFile().mkdirs();
		return file;
	}

	public static void transfer(InputStream in, OutputStream out) throws Exception
	{
		int len;
		byte[] bytes = new byte[1048576];
		while ((len = in.read(bytes)) != - 1)
		{
			out.write(bytes, 0, len);
		}
	}

	public static String readFile(File file) throws Exception
	{
		InputStream in = new FileInputStream(file);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		transfer(in, out);
		in.close();
		return out.toString("utf-8");
	}

	public static String readFile(String path)
	{
		try
		{
			return readFile(new File(path));
		}
		catch (Exception e)
		{
			return null;
		}
	}

	public static void initPaths(String dataDir, String loxFolder)
	{
		LOX_PATH = String.format("%s/%s/", dataDir, loxFolder);
		APP_PATH = LOX_PATH + "app/";
		PATCH_PATH = LOX_PATH + "patch/";
	}

	public static ApplicationEntry[] listApps(PackageManager manager, String expackage)
	{
		List<ApplicationEntry> apps = new ArrayList<ApplicationEntry>();
		for (File appFile: Utils.newFolder(Utils.APP_PATH).listFiles())
		{
			if (! appFile.isDirectory()) continue;
			String packageName = appFile.getName();
			if (packageName.equals(expackage)) continue;
			try
			{
				PackageInfo packageInfo = manager.getPackageInfo(packageName, 0);
				try
				{
					apps.add(new ApplicationEntry(packageInfo, manager));
				}
				catch (Exception e)
				{}
			}
			catch (PackageManager.NameNotFoundException e)
			{
				ApplicationEntry entry = new ApplicationEntry(null, manager);
				entry.packageName = packageName;
				apps.add(entry);
			}
		}
		return apps.toArray(new ApplicationEntry[apps.size()]);
	}

	public static void deleteUninstalleds(PackageManager manager) throws Exception
	{
		for (File appFile: Utils.newFolder(Utils.APP_PATH).listFiles())
		{
			String packageName = appFile.getName();
			try
			{
				manager.getApplicationInfo(packageName, 0);
			}
			catch (PackageManager.NameNotFoundException e)
			{
				deleteFile(appFile);
			}
		}
	}
}


