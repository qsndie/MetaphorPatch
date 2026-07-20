package nea.lox.patchloader;
import de.robv.android.xposed.*;
import de.robv.android.xposed.XC_MethodHook.*;
import android.content.pm.*;

public class PackageParserHook extends XC_MethodHook
{

	@Override
	public void afterHookedMethod(XC_MethodHook.MethodHookParam param)
	{
		PackageInfo packageInfo = (PackageInfo) param.getResult();
		param.setResult(ProxyPackageInfoCreator.getPackageInfo(packageInfo));
	}
}
