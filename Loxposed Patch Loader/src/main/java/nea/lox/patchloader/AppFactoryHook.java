package nea.lox.patchloader;
import de.robv.android.xposed.*;
import de.robv.android.xposed.XC_MethodHook.*;
import android.app.*;
import android.content.pm.*;
import java.io.*;
import android.os.*;
import org.lsposed.lspd.nativebridge.*;

public class AppFactoryHook extends XC_MethodHook
{

	public static AppComponentFactory factory;

	@Override
	public void beforeHookedMethod(XC_MethodHook.MethodHookParam param)
	{
		//XposedBridge.unhookMethod(param.method, this);
		ClassLoader appClassLoader = LoxApplication.appClassLoader;
		ApplicationInfo appInfo = LoxApplication.appInfo;
		AppComponentFactory factory = AppFactoryHook.factory;
		if (factory == null)
		{
			try
			{
				factory = (AppComponentFactory) appClassLoader.loadClass(appInfo.appComponentFactory).newInstance();
			}
			catch (Exception e)
			{
				factory = (AppComponentFactory) XposedHelpers.getStaticObjectField(AppComponentFactory.class, "DEFAULT");
			}
			AppFactoryHook.factory = factory;
		}
		XposedHelpers.setObjectField(LoxApplication.appLoadedApk, "mAppComponentFactory", factory);
		param.setResult(XposedHelpers.callMethod(factory, param.method.getName(), param.args));
	}
}
