package org.metaphorpatch.widget;

import android.widget.*;
import org.metaphorpatch.util.*;
import android.content.*;
import android.view.*;

public class ModuleAdapter extends ArrayAdapter<ModuleEntry>
{
	public ModuleAdapter (Context context)
	{
		super (context, R.layout.item_module);
	}

	@Override
	public View getView(int position, View view, ViewGroup parent)
	{
		Context context = getContext();
		if (view == null)
		{
			view = LayoutInflater.from(context).inflate(R.layout.item_module, parent, false);
		}
		ModuleEntry entry = getItem(position);
		view.findViewById(R.id.icon).setBackground(entry.icon);
		view.<TextView>findViewById(R.id.label).setText(entry.label);
		view.<TextView>findViewById(R.id.package_and_version).setText(entry.versionName + "\n" + entry.packageName);
		view.<TextView>findViewById(R.id.description).setText(entry.description);
		Switch box = view.findViewById(R.id.check);
		box.setChecked(entry.isActive);
		return view;
	}
}

