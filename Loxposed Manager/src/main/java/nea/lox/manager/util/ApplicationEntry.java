package nea.lox.manager.util;

import android.content.pm.*;
import android.graphics.drawable.*;

public class ApplicationEntry
{
	public String packageName;
	public String label;
	public String versionName;
	public Drawable icon;

	public ApplicationEntry (PackageInfo packageInfo, PackageManager manager)
	{
		if (packageInfo != null)
		{
			ApplicationInfo info = packageInfo.applicationInfo;
			label = info.loadLabel(manager).toString();
			versionName = packageInfo.versionName;
			packageName = packageInfo.packageName;
			icon = info.loadIcon(manager);
		}
	}

	public void uninstall ()
	{
		Utils.deleteFile(Utils.APP_PATH + packageName);
	}
}

