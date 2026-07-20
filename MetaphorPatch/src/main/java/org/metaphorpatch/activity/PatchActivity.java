package org.metaphorpatch.activity;
import android.app.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import android.content.res.*;
import android.net.*;
import java.util.*;
import java.util.zip.*;
import pxb.android.axml.*;
import android.view.*;
import nea.lox.*;
import org.metaphorpatch.util.*;
import android.content.pm.*;
import org.json.*;
import java.nio.file.*;
import java.util.jar.*;

public class PatchActivity extends Activity
implements Runnable
{

	static Thread patchingThread;
	static String LOXPOSED_APP_FACTORY;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if (LOXPOSED_APP_FACTORY == null)
		{
			try
			{
				InputStream in = getAssets().open("factory.txt");
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				Utils.transfer(in, out);
				in.close();
				LOXPOSED_APP_FACTORY = new String(out.toByteArray(), "utf-8").trim().replace('/', '.');
			}
			catch (Exception e) {}
		}
		if (patchingThread == null)
		{
			setTitle(R.string.patching_apk);
			patchingThread = new Thread(this);
			patchingThread.start();
		}
		setContentView(new ProgressBar(this));
	}

	@Override
	public void run()
	{
		String errorString = null;
		try
		{
			InputStream in = null;
			Uri uri = getIntent().getData();
			switch (uri.getScheme())
			{
				case "file":
					in = new URL(getIntent().getData().toString()).openStream();
					break;
				case "content":
					in = getContentResolver().openInputStream(uri);
					break;
			}
			AssetManager assets = getAssets();
			Utils.deleteFile(Utils.PATCH_PATH);
			File srcFile = Utils.newFile(Utils.PATCH_PATH + "base_apk");
			Files.copy(in, Paths.get(srcFile.getPath()));
			in.close();
			String packageName = null, factory;
			ZipFile srcZipFile = new ZipFile(srcFile);
			int dexIndex = 2;
			while (true)
			{
				if (srcZipFile.getEntry(String.format("classes%s.dex", dexIndex)) == null)
				{
					break;
				}
				dexIndex ++;
			}
			ZipInputStream zin = new ZipInputStream(new FileInputStream(srcFile));
			ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(Utils.newFile(Utils.LOX_PATH + "patched.apk")));
			ZipEntry entry = null;
			while ((entry = zin.getNextEntry()) != null)
			{
				if (entry.getName().equals("AndroidManifest.xml"))
				{
					ByteArrayOutputStream bout = new ByteArrayOutputStream();
					Utils.transfer(zin, bout);
					byte[] bytes = bout.toByteArray();
					bytes = modifyManifest(bytes);
					zout.putNextEntry(new ZipEntry("AndroidManifest.xml"));
					zout.write(bytes);
					zout.closeEntry();
					entry = new ZipEntry(String.format("classes%s.dex", dexIndex));
					zout.putNextEntry(entry);
					in = assets.open("proxy.dex");
					Utils.transfer(in, zout);
					in.close();
				}
				else
				{
					ZipEntry newEntry = new ZipEntry(entry);
					newEntry.setCompressedSize(-1);
					zout.putNextEntry(newEntry);
					Utils.transfer(zin, zout);
				}
				zin.closeEntry();
				zout.closeEntry();
			}
			zout.closeEntry();
			zout.flush();
			zout.close();
			zin.close();
			PackageInfo info = getPackageManager().getPackageArchiveInfo(srcFile.getPath(), PackageManager.GET_SIGNATURES);
			Signature[] signs = info.signatures;
			factory = info.applicationInfo.appComponentFactory;
			packageName = info.packageName;
			JSONArray signsJson = new JSONArray();
			for (int i = 0; i < signs.length; i ++)
			{
				signsJson.put(signs[i].toCharsString());
			}
			JSONObject configJson = new JSONObject();
			configJson.put("factory", factory);
			configJson.put("signatures", signsJson);
			FileWriter wri = new FileWriter(Utils.PATCH_PATH + "config.json");
			wri.write(configJson.toString());
			wri.flush();
			wri.close();
			File appFolder = Utils.newFolder(Utils.APP_PATH + packageName);
			String[] shouldSaveFiles = {"xsp"};
			for (String shouldSaveFile: shouldSaveFiles)
			{
				File file = new File(appFolder.getPath() + "/" + shouldSaveFile);
				if (file.exists())
				{
					File newFile = Utils.newFile(Utils.PATCH_PATH + shouldSaveFile);
					if (newFile.exists())
					{
						Utils.deleteFile(newFile);
					}
					file.renameTo(newFile);
				}
			}
			Utils.deleteFile(appFolder);
			Utils.newFolder(Utils.PATCH_PATH).renameTo(appFolder);
		}
		catch (Throwable e)
		{
			StringWriter out = new StringWriter();
			e.printStackTrace(new PrintWriter(out));
			out.flush();
			try
			{
				out.close();
			}
			catch (Exception e2)
			{}
			errorString = out.toString();
		}
		patchingThread = null;
		final String finalErrorString = errorString;
		runOnUiThread(new Runnable() {
				@Override
				public void run()
				{
					if (finalErrorString == null)
					{
						setTitle(R.string.finish);
						setContentView(R.layout.activity_patch);
						PatchActivity.this.<TextView> findViewById(R.id.message).setText(getString(R.string.patch_finish_message, Utils.LOX_DIR_NAME));
					} else
					{
						Toast.makeText(PatchActivity.this, finalErrorString, Toast.LENGTH_SHORT).show();
						finishAndRemoveTask();
					}
				}
			});
	}



	public static byte[] modifyManifest(byte[] data) throws IOException
	{
		AxmlReader reader = new AxmlReader(data);
		AxmlWriter writer = new AxmlWriter();

		final Set<String> existingPermissions = new TreeSet<>();
		final boolean[] hasAppComponentFactory = {false};

		reader.accept(new AxmlVisitor(writer) {
				@Override
				public NodeVisitor child(String namespace, String name)
				{
					NodeVisitor nv = super.child(namespace, name);

					if ("uses-permission".equals(name))
					{
						return new NodeVisitor(nv) {
							@Override
							public void attr(String ns, String attrName, int resId, int type, Object obj)
							{
								if ("name".equals(attrName) && obj instanceof String)
								{
									existingPermissions.add((String) obj);
								}
								super.attr(ns, attrName, resId, type, obj);
							}
						};
					}

					if ("manifest".equals(name))
					{
						return new NodeVisitor(nv) {
							@Override
							public NodeVisitor child(String namespace, String name)
							{
								NodeVisitor nv = super.child(namespace, name);

								if ("application".equals(name))
								{
									return new NodeVisitor(nv) {
										@Override
										public void attr(String namespace, String name, int resourceId, int type, Object obj)
										{
											if (resourceId == android.R.attr.appComponentFactory)
											{
												hasAppComponentFactory[0] = true;
												obj = LOXPOSED_APP_FACTORY; // 修改值
											}
											super.attr(namespace, name, resourceId, type, obj);
										}

										@Override
										public void end()
										{
											if (!hasAppComponentFactory[0])
											{
												super.attr("http://schemas.android.com/apk/res/android", "appComponentFactory", android.R.attr.appComponentFactory, AxmlVisitor.TYPE_STRING, LOXPOSED_APP_FACTORY);
											}
											super.end();
										}
									};
								}
								return nv;
							}

							@Override
							public void end()
							{
								super.end();
								//addPermission(this, "android.permission.READ_EXTERNAL_STORAGE");
								//addPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE");
								addPermission(this, "android.permission.MANAGE_EXTERNAL_STORAGE");
							}
						};
					}

					return nv;
				}

				private void addPermission(NodeVisitor parent, String permission)
				{
					if (!existingPermissions.contains(permission))
					{
						NodeVisitor permissionNode = parent.child(null, "uses-permission");
						if (permissionNode != null)
						{
							permissionNode.attr("http://schemas.android.com/apk/res/android", "name", 0x01010003, AxmlVisitor.TYPE_STRING, permission);
							permissionNode.end();
						}
					}
				}
			});

		byte[] modifiedData = writer.toByteArray();
		return modifiedData;
	}
}

