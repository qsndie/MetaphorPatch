package nea.lox.manager.activity;
import android.app.*;
import android.os.*;
import org.json.*;
import java.util.*;
import nea.lox.manager.util.*;
import android.widget.*;
import nea.lox.manager.widget.*;
import android.view.*;
import nea.lox.*;
import java.io.*;
import android.content.pm.*;

public class ModuleScopeActivity extends Activity
implements AdapterView.OnItemClickListener, Runnable
{

	public Set<String> scope;
	public String modPackageName;
	public ApplicationCheckAdapter adapter;
	public JSONArray modulesJson;
	public JSONObject moduleJson;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		String modPackageName = this.modPackageName = getIntent().getStringExtra("package");
		scope = new TreeSet<>();
		try
		{
			JSONArray modsJson = new JSONArray(Utils.readFile(Utils.LOX_PATH + "modules.json"));
			modulesJson = modsJson;
			for (int i = 0; i < modsJson.length(); i ++)
			{
				JSONObject modJson = modsJson.getJSONObject(i);
				moduleJson = modJson;
				if (modJson.getString("package").equals(modPackageName))
				{
					JSONArray scopeJson = modJson.getJSONArray("scope");
					for (int j = 0; j < scopeJson.length(); j ++)
					{
						scope.add(scopeJson.getString(j));
					}
					break;
				}
			}
		}
		catch (Exception e) {}
		adapter = new ApplicationCheckAdapter(this)
		{
			@Override
			public View getView(int position, View view, ViewGroup parent)
			{
				view = super.getView(position, view, parent);
				ApplicationEntry item = getItem(position);
				CheckBox box = view.findViewById(R.id.check);
				if (scope.contains(item.packageName))
				{
					box.setChecked(true);
				}
				else
				{
					box.setChecked(false);
				}
				return view;
			}
		};
		new Thread(this).start();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long itemId)
	{
		ApplicationEntry app = adapter.getItem(position);
		String packageName = app.packageName;
		if (scope.contains(packageName))
		{
			scope.remove(packageName);
		}
		else
		{
			scope.add(packageName);
		}
		adapter.notifyDataSetChanged();
		try
		{
			JSONArray scopeJson = new JSONArray();
			for (String scopePackageName: scope)
			{
				scopeJson.put(scopePackageName);
			}
			moduleJson.put("scope", scopeJson);
			FileWriter out = new FileWriter(Utils.LOX_PATH + "modules.json");
			out.write(modulesJson.toString());
			out.flush();
			out.close();
		}
		catch (Exception e) {}
	}


	@Override
	public void run()
	{
		PackageManager manager = getPackageManager();
		final ApplicationEntry[] apps = Utils.listApps(manager, modPackageName);
		runOnUiThread(new Runnable() {

				@Override
				public void run()
				{
					ModuleScopeActivity act = ModuleScopeActivity.this;
					ListView list = new ListView(act);
					list.setAdapter(adapter);
					list.setOnItemClickListener(act);
					setContentView(list);
					adapter.clear();
					adapter.addAll(apps);
				}
			});
	}
	
}
