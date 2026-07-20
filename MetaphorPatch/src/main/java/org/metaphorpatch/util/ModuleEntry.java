package org.metaphorpatch.util;
import android.content.pm.*;

public class ModuleEntry extends ApplicationEntry
{
	public String description;
	public boolean isActive;

	public ModuleEntry (PackageInfo packageInfo, PackageManager manager)
	{
		super (packageInfo, manager);
		try
		{
			description = packageInfo.applicationInfo.metaData.getString("xposeddescription");
		}
		catch (Exception e) {}
	}
}


