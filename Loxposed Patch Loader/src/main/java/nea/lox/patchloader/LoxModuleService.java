package nea.lox.patchloader;
import org.lsposed.lspd.service.*;
import java.util.*;
import org.lsposed.lspd.models.*;
import android.os.*;
import java.io.*;
import org.json.*;
import org.lsposed.lspatch.util.*;
import org.lsposed.lspd.hooker.*;
import de.robv.android.xposed.*;
import java.util.zip.*;
import java.nio.channels.*;
import java.nio.*;
import android.system.*;
import io.github.libxposed.api.*;
import android.content.pm.*;

public class LoxModuleService extends ILSPApplicationService.Stub
implements XposedModuleInterface.PackageLoadedParam
{

	public List<Module> modules, legacyMods;

	public LoxModuleService(String modulesConfigFilePath)
	{
		modules = new ArrayList<>();
		legacyMods = new ArrayList<>();
		String packageName = LoxApplication.appInfo.packageName;
		ClassLoader appClassLoader = LoxApplication.appClassLoader;
		try
		{
			File moduleConfigFile = new File(modulesConfigFilePath);
			InputStream in = new FileInputStream(moduleConfigFile);
			byte[] bytes = new byte[in.available()];
			in.read(bytes);
			in.close();
			JSONArray data = new JSONArray(new String(bytes, "utf-8"));
			for (int i = 0; i < data.length(); i ++)
			{
				JSONObject modJson = data.getJSONObject(i);
				String modPackageName = modJson.getString("package");
				if (modJson.getBoolean("active"))
				{
					JSONArray scopeJson = modJson.getJSONArray("scope");
					if (packageName.equals(modPackageName))
					{
						XposedHelpers.findAndHookMethod("android.app.ContextImpl", appClassLoader, "checkMode", int.class, new PreferencesHook(false));
						XposedHelpers.findAndHookMethod("android.app.ContextImpl", appClassLoader, "getPreferencesDir", new PreferencesHook(true));
						scopeJson.put(0, packageName);
					}
					for (int j = 0; j < scopeJson.length(); j ++)
					{
						if (packageName.equals(scopeJson.getString(j)))
						{
							Module module = new Module();
							module.apkPath = modJson.getString("path");
							module.packageName = modPackageName;
							PreLoadedApk apk = loadModule(module.apkPath);
							module.file = apk;
							if (apk.legacy)
							{
								legacyMods.add(module);
							}
							else
							{
								modules.add(module);
							}
							break;
						}
					}
				}
			}
		}
		catch (Exception e)
		{}
	}

	@Override
	public List<Module> getLegacyModulesList() throws RemoteException
	{
		return legacyMods;
	}

	@Override
	public List<Module> getModulesList() throws RemoteException
	{
		return modules;
	}

	@Override
	public String getPrefsPath(String packageName) throws RemoteException
	{
		String path = String.format("%s%s/xsp/", LoxApplication.loAppsPath, packageName);
		return path;
	}

	@Override
	public boolean isLogMuted() throws RemoteException
	{
		return false;
	}

	@Override
	public ParcelFileDescriptor requestInjectedManagerBinder(List<IBinder> p1) throws RemoteException
	{
		return null;
	}

	@Override
	public IBinder asBinder()
	{
		return this;
	}


	@Override
	public ApplicationInfo getApplicationInfo()
	{
		return LoxApplication.appInfo;
	}

	@Override
	public ClassLoader getClassLoader()
	{
		return LoxApplication.appClassLoader;
	}

	@Override
	public ClassLoader getDefaultClassLoader()
	{
		return LoxApplication.appClassLoader;
	}

	@Override
	public String getPackageName()
	{
		return LoxApplication.appInfo.packageName;
	}

	@Override
	public boolean isFirstPackage()
	{
		return true;
	}

	private static void readDexes(ZipFile apkFile, List<SharedMemory> preLoadedDexes)
	{
		int secondary = 2;
		for (ZipEntry dexFile = apkFile.getEntry("classes.dex"); dexFile != null; dexFile = apkFile.getEntry(String.format("classes%s.dex", secondary)), secondary++)
		{
			try
			{
				InputStream in = apkFile.getInputStream(dexFile);
				SharedMemory memory = SharedMemory.create(null, in.available());
				ByteBuffer byteBuffer = memory.mapReadWrite();
				Channels.newChannel(in).read(byteBuffer);
				SharedMemory.unmap(byteBuffer);
				memory.setProtect(OsConstants.PROT_READ);
				preLoadedDexes.add(memory);
			}
			catch (Exception e)
			{
			}
		}
	}

	private static void readName(ZipFile apkFile, String initName, List<String> names)
	{
		ZipEntry initEntry = apkFile.getEntry(initName);
		if (initEntry == null) return;
		try
		{
			InputStream in = apkFile.getInputStream(initEntry);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			String name;
			while ((name = reader.readLine()) != null)
			{
				name = name.trim();
				if (name.isEmpty() || name.startsWith("#")) continue;
				names.add(name);
			}
		}
		catch (Exception e)
		{
		}
	}

	public static PreLoadedApk loadModule(String path)
	{
		if (path == null) return null;
		PreLoadedApk file = new PreLoadedApk();
		List<SharedMemory> preLoadedDexes = new ArrayList<SharedMemory>();
		List<String> moduleClassNames = new ArrayList<String>(1);
		List<String> moduleLibraryNames = new ArrayList<String>(1);
		try
		{
			ZipFile apkFile = new ZipFile(path);
			readDexes(apkFile, preLoadedDexes);
			readName(apkFile, "META-INF/xposed/java_init.list", moduleClassNames);
			if (moduleClassNames.isEmpty())
			{
				file.legacy = true;
				readName(apkFile, "assets/xposed_init", moduleClassNames);
				readName(apkFile, "assets/native_init", moduleLibraryNames);
			}
			else
			{
				file.legacy = false;
				readName(apkFile, "META-INF/xposed/native_init.list", moduleLibraryNames);
			}
		}
		catch (Exception e)
		{
		}
		if (preLoadedDexes.isEmpty()) return null;
		if (moduleClassNames.isEmpty()) return null;
		file.preLoadedDexes = preLoadedDexes;
		file.moduleClassNames = moduleClassNames;
		file.moduleLibraryNames = moduleLibraryNames;
		return file;
	}
}
