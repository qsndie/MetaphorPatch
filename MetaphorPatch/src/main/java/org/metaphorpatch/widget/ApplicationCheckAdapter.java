import org.metaphorpatch.R;
package org.metaphorpatch.widget;

import android.widget.*;
import android.content.pm.*;
import android.content.*;
import android.view.*;
import org.metaphorpatch.util.*;

public class ApplicationCheckAdapter extends ArrayAdapter<ApplicationEntry>
{
	public ApplicationCheckAdapter (Context context)
	{
		super (context, R.layout.item_application_check);
	}

	@Override
	public View getView(int position, View view, ViewGroup parent)
	{
		Context context = getContext();
		if (view == null)
		{
			view = LayoutInflater.from(context).inflate(R.layout.item_application_check, parent, false);
		}
		ApplicationEntry entry = getItem(position);
		view.findViewById(R.id.icon).setBackground(entry.icon);
		view.<TextView>findViewById(R.id.label).setText(String.valueOf(entry.label));
		view.<TextView>findViewById(R.id.package_and_version).setText(entry.versionName + "\n" + entry.packageName);
		return view;
	}
}

