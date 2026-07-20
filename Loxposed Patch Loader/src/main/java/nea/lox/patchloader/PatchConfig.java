package nea.lox.patchloader;
import android.content.pm.*;
import org.json.*;

public class PatchConfig
{
	public String appComponentFactory;
	public Signature[] signatures;

	public PatchConfig(JSONObject json)
	{
		try
		{
			try
			{
				appComponentFactory = json.getString("factory");
			}
			catch (Exception e) {}
			JSONArray signsJson = json.getJSONArray("signatures");
			signatures = new Signature[signsJson.length()];
			for (int i = 0; i < signatures.length; i ++)
			{
				signatures[i] = new Signature(signsJson.getString(i));
			}
		}
		catch (Exception e)
		{}
	}
}
