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
public class ProxyPackageInfoCreator extends XC_MethodHook
{

	public static void proxy()
	{
		final Parcelable.Creator<PackageInfo> originalPackageCreator = PackageInfo.CREATOR;
		ProxyPackageInfoCreator proxiedPackageCreator = new ProxyPackageInfoCreator();
		XposedHelpers.findAndHookMethod(originalPackageCreator.getClass(), "createFromParcel", Parcel.class, proxiedPackageCreator);
	}

	@Override
	public void afterHookedMethod(XC_MethodHook.MethodHookParam param)
	{
		param.setResult(getPackageInfo((PackageInfo) param.getResult()));
	}

	public static PackageInfo getPackageInfo(PackageInfo info)
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
						info.applicationInfo.appComponentFactory = config.appComponentFactory;
						Signature[] signs = config.signatures;
						int signsLen = signs.length;
						if (info.signatures != null) info.signatures = Arrays.copyOf(signs, signsLen);
						if (info.signingInfo != null)
						{
							Object details = XposedHelpers.getObjectField(info.signingInfo, "mSigningDetails");
							XposedHelpers.setObjectField(details, "mSignatures", Arrays.copyOf(signs, signsLen));
							XposedHelpers.setObjectField(details, "mPastSigningCertificates", Arrays.copyOf(signs, signsLen));
						}
					}
				}
				catch (Exception e)
				{}
			}
		}
		return info;
	}
}
