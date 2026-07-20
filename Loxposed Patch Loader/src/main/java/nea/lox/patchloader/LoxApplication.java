package nea.lox.patchloader;
import static de.robv.android.xposed.XposedHelpers.*;
import android.content.pm.*;
import java.lang.reflect.*;
import java.util.*;
import org.lsposed.lspd.core.*;
import android.os.*;
import org.lsposed.lspd.nativebridge.*;
import org.json.*;
import java.io.*;
import android.content.*;
import java.nio.file.*;
import android.app.*;
import de.robv.android.xposed.*;
import android.content.res.*;
import de.robv.android.xposed.callbacks.*;
import dalvik.system.*;
import io.github.libxposed.api.*;
import org.lsposed.lspd.impl.*;
import java.util.zip.*;

public class LoxApplication
//implements Thread.UncaughtExceptionHandler
{

	/*@Override
	public void uncaughtException(Thread p1, Throwable p2)
	{
		try
		{
			FileWriter out = new FileWriter(new File(loPath, "patch_error.log"));
			p2.printStackTrace(new PrintWriter(out));
			out.flush();
			out.close();
		}
		catch (IOException e)
		{}
	}*/

	public static ApplicationInfo appInfo;
	public static String trueApkPath, loAppsPath, loPath, selfApkPath;
	public static Map<String, PatchConfig> patchConfigs;
	public static Object appLoadedApk;
	public static ClassLoader appClassLoader;

	public static void load() throws Throwable
	{
		ClassLoader loaderClassLoader = XposedBridge.class.getClassLoader();
		patchConfigs = new TreeMap<>();
		Class<?> activityThreadClass = loaderClassLoader.loadClass("android.app.ActivityThread"), loadedApkClass = loaderClassLoader.loadClass("android.app.LoadedApk");
		Object thread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
		Object bound = getObjectField(thread, "mBoundApplication");
		Object appLoadedApk = getObjectField(bound, "info");
		ClassLoader metaClassLoader = (ClassLoader) getObjectField(appLoadedApk, "mClassLoader");
		Class<?> metaClass = metaClassLoader.loadClass("nea.lox.metaloader.LoxMetaLoader");
		Class<?> factoryClass = (Class<?>) getStaticObjectField(metaClass, "factoryClass");
		LoxApplication.appClassLoader = metaClassLoader.getParent();
		LoxApplication.appLoadedApk = appLoadedApk;
		ApplicationInfo appInfo = (ApplicationInfo) getObjectField(bound, "appInfo");
		LoxApplication.appInfo = appInfo;
		String packageName = appInfo.packageName;
		trueApkPath = appInfo.sourceDir;
		String loPath = String.format("%s/%s/", Environment.getExternalStorageDirectory().getPath(), (String) getStaticObjectField(metaClass, "folder"));
		LoxApplication.loPath = loPath;
		String loAppsPath = loPath + "app/";
		LoxApplication.loAppsPath = loAppsPath;
		String selfAppPath = loAppsPath + packageName;
		selfApkPath = selfAppPath + "/base_apk";
		if (new File(selfApkPath).isFile())
		{
			SigBypass.enableOpenatHook(trueApkPath, selfApkPath);
		}
		PatchConfig selfPatchConfig = getPatchConfig(appInfo.packageName);
		appInfo.appComponentFactory = selfPatchConfig.appComponentFactory;
		try
		{
			LoxModuleService service = new LoxModuleService(loPath + "modules.json");
			Startup.initXposed(false, appInfo.processName, appInfo.dataDir, service);
			Startup.bootstrapXposed();
			XposedInit.class.getDeclaredMethod("loadModules", activityThreadClass).invoke(null, thread);
			setObjectField(appLoadedApk, "mClassLoader", appClassLoader);
			loaderClassLoader.loadClass("org.lsposed.lspatch.loader.LSPLoader").getMethod("initModules", loadedApkClass).invoke(null, appLoadedApk);
			LSPosedContext.callOnPackageLoaded(service);
		}
		catch (Throwable e)
		{}
		XposedBridge.hookAllMethods(loaderClassLoader.loadClass("android.content.pm.PackageParser"), "generatePackageInfo", new PackageParserHook());
		ProxyApplicationInfoCreator.proxy();
		ProxyPackageInfoCreator.proxy();
		setObjectField(appLoadedApk, "mClassLoader", null);
		for (Method method: factoryClass.getDeclaredMethods())
		{
			XposedBridge.hookMethod(method, new AppFactoryHook());
		}
		//Thread.setDefaultUncaughtExceptionHandler(new LoxApplication());
	}

	public static PatchConfig getPatchConfig(String packageName) throws Exception
	{
		PatchConfig config = patchConfigs.get(packageName);
		if (config == null)
		{
			try
			{
				File file = new File(String.format("%s%s/config.json", loAppsPath, packageName));
				if (! file.isFile()) return null;
				InputStream in = new FileInputStream(file);
				byte[] bytes = new byte[in.available()];
				in.read(bytes);
				in.close();
				JSONObject json = new JSONObject(new String(bytes, "utf-8"));
				config = new PatchConfig(json);
				patchConfigs.put(packageName, config);
			}
			catch (Throwable e)
			{
				return null;
			}
		}
		return config;
	}
}
