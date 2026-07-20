package nea.lox.metaloader;

import java.io.*;
import android.app.*;
import android.os.*;
import java.util.zip.*;
import java.lang.reflect.*;
import java.util.*;
import android.content.*;
import android.content.pm.*;
import org.json.*;
import org.lsposed.lspatch.metaloader.*;
import dalvik.system.*;

public class LoxMetaLoader
{

	public static String folder;
	public static Class<?> factoryClass;

	public static void load (Class<?> factoryClass, String folderName) throws Throwable
	{
		ClassLoader appClassLoader = factoryClass.getClassLoader();
		if (appClassLoader.getClass() != PathClassLoader.class)
		{
			return;
		}
		ClassLoader metaClassLoader = LoxMetaLoader.class.getClassLoader();
		Class<?> activityThreadClass = metaClassLoader.loadClass("android.app.ActivityThread");//, loadedApkClass = metaClassLoader.loadClass("android.app.LoadedApk");
		Object thread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
		Object bound = getField(thread, "mBoundApplication");
		Object appLoadedApk = getField(bound, "info");
		/*if (getField(appLoadedApk, "mDefaultClassLoader") != appClassLoader)
		{
			return;
		}*/
		LoxMetaLoader.factoryClass = factoryClass;
		folder = folderName;
		File sdcard = Environment.getExternalStorageDirectory();
		InputStream in = new FileInputStream(new File(sdcard, String.format("%s/loader/lib.json", folderName)));
		byte[] bytes = new byte[in.available()];
		in.read(bytes);
		in.close();
		JSONObject libPaths = new JSONObject(new String(bytes, "utf-8"));
		in = new FileInputStream(new File(sdcard, String.format("%s/loader/patch.dex", folderName)));
		bytes = new byte[in.available()];
		in.read(bytes);
		in.close();
		LSPAppComponentFactoryStub.dex = bytes;
		Class<?> VMRuntime = Class.forName("dalvik.system.VMRuntime");
		Method getRuntime = VMRuntime.getDeclaredMethod("getRuntime");
		getRuntime.setAccessible(true);
		Method vmInstructionSet = VMRuntime.getDeclaredMethod("vmInstructionSet");
		vmInstructionSet.setAccessible(true);
		String arch = (String) vmInstructionSet.invoke(getRuntime.invoke(null));
		setField(appLoadedApk, "mClassLoader", metaClassLoader);
		System.load(libPaths.getString(arch));
		setField(appLoadedApk, "mClassLoader", null);
	}

	public static Object getField(Object thisObject, String fieldName) throws Exception
	{
		Field field = findField(thisObject.getClass(), fieldName);
		return field.get(thisObject);
	}

	public static void setField(Object thisObject, String fieldName, Object fieldValue) throws Exception
	{
		Field field = findField(thisObject.getClass(), fieldName);
		field.set(thisObject, fieldValue);
	}

	public static Field findField(Class<?> c, String name)
	{
		for (;c != null;c = c.getSuperclass())
		{
			try
			{
				Field field = c.getDeclaredField(name);
				field.setAccessible(true);
				return field;
			}
			catch (NoSuchFieldException ignored)
			{
			}
		}
		return null;
	}
}
