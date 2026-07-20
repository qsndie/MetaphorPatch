package org.metaphorpatch.activity;
import org.metaphorpatch.R;
import android.app.*;
import android.os.*;
import android.view.*;
import android.content.res.*;
import java.io.*;
import org.metaphorpatch.util.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import android.content.*;
import android.provider.*;
import android.content.pm.*;
import android.net.*;
import org.metaphorpatch.widget.*;

public class MainActivity extends Activity
implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener
{

public ModuleAdapter adapter;
public ListView list;

@Override
protected void onCreate(Bundle savedInstanceState)
{
super.onCreate(savedInstanceState);
setTitle(R.string.app_name);
if (Environment.isExternalStorageManager())
{
create();
} else
{
Toast.makeText(this, R.string.permission_first, Toast.LENGTH_SHORT).show();
Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
startActivityForResult(intent, 1);
}
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data)
{
super.onActivityResult(requestCode, resultCode, data);
if (requestCode == 1)
{
if (Environment.isExternalStorageManager())
{
create();
} else
{
finishAndRemoveTask();
}
}
}

@Override
public boolean onCreateOptionsMenu(Menu menu)
{
getMenuInflater().inflate(R.layout.menu_main, menu);
return true;
}

@Override
public boolean onOptionsItemSelected(MenuItem item)
{
int id = item.getItemId();
if (id == R.id.reloader)
{
final ProgressDialog loadingDialog = newLoadingDialog();
new Thread()
{
public void run()
{
AssetManager assets = getAssets();
String errorString = null;
try
{
Map<String, String> archToLib = new HashMap<>();
archToLib.put("arm", "armeabi-v7a");
archToLib.put("arm64", "arm64-v8a");
archToLib.put("x86", "x86");
archToLib.put("x86_64", "x86_64");
String sourceDir = getPackageCodePath();
JSONObject libJson = new JSONObject();
for (Map.Entry<String, String> entry: archToLib.entrySet())
{
String key = entry.getKey(), value = entry.getValue();
libJson.put(key, String.format("%s!/assets/so/%s/libxposed.so", sourceDir, value));
}
FileWriter out = new FileWriter(Utils.newFile(Utils.LOX_PATH + "loader/lib.json"));
out.write(libJson.toString());
out.flush();
out.close();
for (String fileName: assets.list("loader"))
{
fileName = "loader/" + fileName;
InputStream in = assets.open(fileName);
OutputStream outs = new FileOutputStream(Utils.newFile(Utils.LOX_PATH + fileName));
Utils.transfer(in, outs);
in.close();
outs.flush();
outs.close();
}
}
catch (Exception e)
{
errorString = e.toString();
}
final String finalErrorString = errorString;
runOnUiThread(new Runnable() {

@Override
public void run()
{
loadingDialog.cancel();
if (finalErrorString != null)
{
Toast.makeText(MainActivity.this, finalErrorString, Toast.LENGTH_SHORT).show();
}
}
});
}
}.start();
}
else if (id == R.id.reload_modules)
{
reloadModules();
}
else if (id == R.id.delete_uninstalled)
{
deleteUninstalleds();
}
return true;
}

public void reloadModules()
{
final ProgressDialog loadingDialog = newLoadingDialog();
new Thread()
{
public void run()
{
String errorString = null;
ModuleEntry[] modsArray = {};
try
{
File file = Utils.newFile(Utils.LOX_PATH + "modules.json");
JSONArray originJson = null;
try
{
originJson = new JSONArray(Utils.readFile(file));
}
catch (Exception e)
{
originJson = new JSONArray();
}
PackageManager manager = getPackageManager();
List<PackageInfo> apps = manager.getInstalledPackages(PackageManager.GET_META_DATA);
JSONObject scopes = new JSONObject();
JSONObject actives = new JSONObject();
for (int i = 0; i < originJson.length(); i ++)
{
try
{
JSONObject modJson = originJson.getJSONObject(i);
String name = modJson.getString("package");
scopes.put(name, modJson.getJSONArray("scope"));
actives.put(name, modJson.getBoolean("active"));
}
catch (Exception e)
{}
}
JSONArray json = new JSONArray();
List<ModuleEntry> modules = new ArrayList<>();
for (PackageInfo app: apps)
{
try
{
if (app.applicationInfo.metaData.containsKey("xposedminversion"))
{
ModuleEntry entry = new ModuleEntry(app, manager);
modules.add(entry);
JSONObject modJson = new JSONObject();
String packageName = app.packageName;
modJson.put("package", packageName);
modJson.put("path", app.applicationInfo.sourceDir);
if (scopes.has(packageName))
{
modJson.put("scope", scopes.getJSONArray(app.packageName));
} else
{
modJson.put("scope", new JSONArray());
}
if (actives.has(packageName))
{
modJson.put("active", actives.getBoolean(packageName));
} else
{
modJson.put("active", false);
}
entry.isActive = modJson.getBoolean("active");
json.put(modJson);
}
}
catch (Exception e)
{}
}
modsArray = modules.toArray(new ModuleEntry[modules.size()]);
FileWriter out = new FileWriter(file);
out.write(json.toString());
out.flush();
out.close();
}
catch (Exception e)
{
errorString = e.toString();
}
final String finalErrorString = errorString;
final ModuleEntry[] finalModsArray = modsArray;
runOnUiThread(new Runnable() {

@Override
public void run()
{
loadingDialog.cancel();
adapter.clear();
adapter.addAll(finalModsArray);
if (finalErrorString != null)
{
Toast.makeText(MainActivity.this, finalErrorString, Toast.LENGTH_SHORT).show();
}
}
});
}
}.start();
}

public ProgressDialog newLoadingDialog()
{
ProgressDialog loadingDialog = new ProgressDialog(this);
loadingDialog.setCancelable(false);
loadingDialog.setMessage(getString(R.string.loading));
loadingDialog.show();
return loadingDialog;
}

public void create()
{
setContentView(R.layout.activity_main);
list = findViewById(R.id.list);
adapter = new ModuleAdapter(this);
list.setAdapter(adapter);
list.setOnItemClickListener(this);
list.setOnItemLongClickListener(this);
reloadModules();
}

@Override
public void onItemClick(AdapterView<?> parent, View view, int position, long itemId)
{
int id = parent.getId();
if (id == R.id.list)
{
ModuleEntry mod = (ModuleEntry) parent.getItemAtPosition(position);
mod.isActive = ! mod.isActive;
try
{
String path = Utils.LOX_PATH + "modules.json";
JSONArray json = new JSONArray(Utils.readFile(path));
for (int i = 0; i < json.length(); i ++)
{
JSONObject modJson = json.getJSONObject(i);
if (modJson.getString("package").equals(mod.packageName))
{
modJson.put("active", mod.isActive);
break;
}
}
FileWriter out = new FileWriter(path);
out.write(json.toString());
out.flush();
out.close();
}
catch (Exception e)
{}
adapter.notifyDataSetChanged();
}
}


@Override
public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long itemId)
{
int id = parent.getId();
if (id == R.id.list)
{
final ModuleEntry mod = (ModuleEntry) parent.getItemAtPosition(position);
PopupMenu popup = new PopupMenu(this, view);
Menu menu = popup.getMenu();
menu.add(R.string.modules_scope).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {

@Override
public boolean onMenuItemClick(MenuItem item)
{
startActivity(new Intent(MainActivity.this, ModuleScopeActivity.class).putExtra("package", mod.packageName));
return true;
}
});
menu.add(R.string.module_settings).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {

@Override
public boolean onMenuItemClick(MenuItem item)
{
PackageManager pm = getPackageManager();
Intent intent = new Intent();
intent.setAction(Intent.ACTION_MAIN);
intent.addCategory("de.robv.android.xposed.category.MODULE_SETTINGS");
intent.setPackage(mod.packageName);
List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
if (!resolveInfos.isEmpty())
{
ResolveInfo resolveInfo = resolveInfos.get(0);
String activityName = resolveInfo.activityInfo.name;
Intent launchIntent = new Intent();
launchIntent.setClassName(mod.packageName, activityName);
launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
startActivity(launchIntent);
}
return true;
}
});
popup.show();
return true;
}
return false;
}

public void deleteUninstalleds ()
{
final ProgressDialog dialog = newLoadingDialog();
new Thread()
{
public void run ()
{
try
{
PackageManager manager = getPackageManager();
Utils.deleteUninstalleds(manager);
}
catch (Exception e) {}
runOnUiThread(new Runnable() {

@Override
public void run()
{
dialog.cancel();
}
});
}
}.start();
}
}
