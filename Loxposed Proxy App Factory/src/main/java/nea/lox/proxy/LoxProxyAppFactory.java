package nea.lox.proxy;
import dalvik.system.*;
import android.os.*;
import java.io.*;
import android.app.*;
import android.content.pm.*;
import android.content.*;
import java.nio.*;

public class LoxProxyAppFactory extends AppComponentFactory
implements Thread.UncaughtExceptionHandler
{

	@Override
	public void uncaughtException(Thread thread, Throwable th)
	{
		try
		{
			FileWriter out = new FileWriter(new File(Environment.getExternalStorageDirectory(), "myapp_error.log"));
			th.printStackTrace(new PrintWriter(out));
			out.flush();
			out.close();
		}
		catch (Exception e) {}
	}
	
	static
	{
		Thread.setDefaultUncaughtExceptionHandler(new LoxProxyAppFactory());
		try
		{
			Class<?> factoryClass = LoxProxyAppFactory.class;
			ClassLoader myClassLoader = factoryClass.getClassLoader();
			if (myClassLoader.getClass() == PathClassLoader.class)
			{
				String folderName = "Loxposed";
				InputStream in = new FileInputStream(new File(Environment.getExternalStorageDirectory(), String.format("%s/loader/meta.dex", folderName)));
				byte[] bytes = new byte[in.available()];
				in.read(bytes);
				in.close();
				ClassLoader metaLoader = new InMemoryDexClassLoader(ByteBuffer.wrap(bytes), myClassLoader);
				Class<?> metaClass = metaLoader.loadClass("nea.lox.metaloader.LoxMetaLoader");
				metaClass.getMethod("load", Class.class, String.class).invoke(null, factoryClass, folderName);
			}
		}
		catch (Throwable e)
		{
		}
	}

	@Override
	public ClassLoader instantiateClassLoader(ClassLoader cl, ApplicationInfo aInfo)
	{
		return cl;
	}

	@Override
	public Application instantiateApplication(ClassLoader cl, String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException
	{
		return null;
	}

	@Override
	public Activity instantiateActivity(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException
	{
		return null;
	}

	@Override
	public Service instantiateService(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException
	{
		return null;
	}

	@Override
	public BroadcastReceiver instantiateReceiver(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException
	{
		return null;
	}

	@Override
	public ContentProvider instantiateProvider(ClassLoader cl, String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException
	{
		return null;
	}
}
