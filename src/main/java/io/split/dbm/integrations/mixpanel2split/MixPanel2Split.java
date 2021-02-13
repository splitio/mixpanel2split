package io.split.dbm.integrations.mixpanel2split;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class MixPanel2Split {

	public void execute(String start, String end, final JSONObject config) throws Exception {

		OkHttpClient client = new OkHttpClient.Builder()
				.authenticator(new Authenticator() {
			        public Request authenticate(Route route, Response response) throws IOException {
			            String credential = Credentials.basic(config.getString("mixpanel.project.api.secret"), "");
			            return response.request().newBuilder().header("Authorization", credential).build();
			        }
			    })
				.connectTimeout(30, TimeUnit.SECONDS)
				.readTimeout(120, TimeUnit.SECONDS)
				.build();

		String uri = "https://data.mixpanel.com/api/2.0/export?from_date=" + start + "&to_date=" + end;	
		System.out.println(uri);
		Request request = new Request.Builder()
				.url(uri)
				.build();
		
		Response response = client.newCall(request).execute();
		System.out.println("success getting export?\t\t" + response.code());

		JSONArray rawEvents = new JSONArray();
		String body = response.body().string();
		BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body.getBytes())));
		String line = null;
		while((line = reader.readLine()) != null) {
			//System.out.println("line: " + line);
			rawEvents.put(new JSONObject(line));
		}

		JSONArray splitEvents = new JSONArray();
		for(int i = 0; i < rawEvents.length(); i++) {
			JSONObject rawEvent = rawEvents.getJSONObject(i);
			JSONObject rawProperties = rawEvent.getJSONObject("properties");

			JSONObject splitEvent = new JSONObject();
			splitEvent.put("key", rawProperties.getString(config.getString("key")));
			splitEvent.put("trafficTypeName", config.getString("trafficType"));
			splitEvent.put("eventTypeId", cleanEventTypeId(rawEvent.getString("event")));
			splitEvent.put("value", rawProperties.has("value") ? rawProperties.get("value") : 0);
			splitEvent.put("environmentName", config.getString("environment"));
			splitEvent.put("timestamp", ("" + rawProperties.getLong("time")) + "000" );
			Map<String, Object> properties = new TreeMap<String, Object>();
			putProperties(properties, config.getString("eventPrefix"), rawProperties);
			splitEvent.put("properties", properties);

			splitEvents.put(splitEvent);
		}

//		JSONArray truncatedSplitEvents = new JSONArray();
//		for(int j = 0; j < 5; j++) {
//			truncatedSplitEvents.put(splitEvents.get(j));
//		}
//		System.out.println(truncatedSplitEvents.toString(2));
		CreateEvents creator = new CreateEvents(config.getString("split.admin.api.key"), 1000);
		creator.doPost(splitEvents);		
	}

	private String cleanEventTypeId(String eventName) {
		String result = "";

		char letter;
		for(int i = 0; i < eventName.length(); i++) {
			letter = eventName.charAt(i);
			if(!Character.isAlphabetic(letter)
					&& !Character.isDigit(letter)
					&& letter != '-' && letter != '_' && letter != '.') {
				letter = 'm';
			}
			result += "" + letter;
		}

		return result;
	}

	public static String readFile(String path)
			throws IOException
	{
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, Charset.defaultCharset());
	}

	private void putProperties(Map<String, Object> properties, String prefix, JSONObject obj) {
		for(String k : obj.keySet()) {
			if(!(obj.get(k) instanceof JSONArray)) {
				properties.put(prefix + k, obj.get(k));
			} else {
				JSONArray array = obj.getJSONArray(k);
				properties.put(prefix + k, array.toString());
			}
		}
	}
}