package nea.lox.patchloader;
import de.robv.android.xposed.*;
import de.robv.android.xposed.XC_MethodHook.*;
import static org.lsposed.lspd.core.ApplicationServiceClient.serviceClient;
import java.io.*;

public class PreferencesHook extends XC_MethodHook
{
	public boolean before;

	public PreferencesHook (boolean before)
	{
		this.before = before;
	}

	@Override
	public void afterHookedMethod(XC_MethodHook.MethodHookParam param)
	{
		if (before) return;
		if (((int) param.args[0] & 1) != 0)
		{
			param.setThrowable(null);
		}
	}

	@Override
	public void beforeHookedMethod(XC_MethodHook.MethodHookParam param)
	{
		if (before)
		{
			param.setResult(new File(serviceClient.getPrefsPath(LoxApplication.appInfo.packageName)));
		}
	}
}
