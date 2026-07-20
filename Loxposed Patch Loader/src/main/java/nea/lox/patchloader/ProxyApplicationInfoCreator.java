package nea.lox.patchloader;
import android.os.*;
import android.content.pm.*;
import android.content.*;
import java.lang.reflect.*;
import java.util.*;
import android.content.pm.PackageManager.*;
import java.io.*;
import org.json.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.XC_MethodHook.*;

public class ProxyApplicationInfoCreator extends XC_MethodHook
{

	public static void proxy()
	{
		final Parcelable.Creator<ApplicationInfo> originalAppCreator = ApplicationInfo.CREATOR;
		ProxyApplicationInfoCreator proxiedAppCreator = new ProxyApplicationInfoCreator();
		XposedHelpers.findAndHookMethod(originalAppCreator.getClass(), "createFromParcel", Parcel.class, proxiedAppCreator);
	}

	@Override
	public void afterHookedMethod(XC_MethodHook.MethodHookParam param)
	{
		param.setResult(getApplicationInfo((ApplicationInfo) param.getResult()));
	}

	public static ApplicationInfo getApplicationInfo(ApplicationInfo info)
	{
		if (info != null)
		{
			String packageName = info.packageName;
			if (packageName != null)
			{
				try
				{
					PatchConfig config = LoxApplication.getPatchConfig(packageName);
					if (config != null)
					{
						info.appComponentFactory = config.appComponentFactory;
					}
				}
				catch (Exception e)
				{}
			}
		}
		return info;
	}
}
