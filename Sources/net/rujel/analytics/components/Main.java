// Generated by the WOLips Templateengine Plug-in at 21.01.2016 11:39:32
package net.rujel.analytics.components;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOHTTPConnection;
import com.webobjects.appserver.WOMessage;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCoding;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;

import net.rujel.reusables.SettingsReader;
import net.rujel.reusables.Various;
import com.webobjects.appserver.WOActionResults;

public class Main extends WOComponent {
	private static final long serialVersionUID = 1L;
	public static final DateFormat idFormat = new SimpleDateFormat("yyMMdd_HHmmss.SSS");
	public static final DateFormat dateTimeFormat = new SimpleDateFormat(
			SettingsReader.stringForKeyPath("ui.dateTimeFormat", "dd.MM.yyyy HH:mm:ss"));
	
	public static final int QUERY_ISSUED = 0;
	public static final int RESPONSE_RECEIVED = 1;
	public static final int QUERY_TIMED_OUT = -1;
	public static final int AWAITING_RESPONSE = 2;
	public static final int QUERY_QUEUED = 3;
	
	protected File home;
	public NSMutableArray schools = new NSMutableArray();
	public NSMutableDictionary schoolByID;
	public NSMutableArray reports = new NSMutableArray();
	public NSMutableArray queries = new NSMutableArray();
	public Object item;
	public NSMutableDictionary query;
	public NSMutableDictionary currReport;
	public NSMutableDictionary currSchool;
	public NSMutableDictionary paramsDict = new NSMutableDictionary(eduYearForDate(null),"eduYear");
	public NSMutableDictionary tmpDict = new NSMutableDictionary();

	public Main(WOContext context) {
		super(context);
		
		String homePath = SettingsReader.stringForKeyPath("analytics.workingDir", null);
		if(homePath == null)
			throw new IllegalStateException("Analytics working directory is not defined");
		homePath = Various.convertFilePath(homePath);
		home = new File(homePath);
		if(!home.canRead())
			throw new IllegalStateException("Can't read working directory");
		File dir = new File(home,"school");
		if(dir.exists()) {
			JSONObject.Utility.dictsFromJSON(dir,schools, false);
		}
		if(schools.count() == 1) {
			currSchool = (NSMutableDictionary)schools.objectAtIndex(0);
			schoolByID = new NSMutableDictionary(currSchool,currSchool.valueForKey("schoolID"));
			schools.removeAllObjects();
		} else {
			Enumeration enu = schools.objectEnumerator();
			schoolByID = new NSMutableDictionary(schools.count());
			while (enu.hasMoreElements()) {
				NSDictionary scl = (NSDictionary) enu.nextElement();
				schoolByID.setObjectForKey(scl, scl.valueForKey("schoolID"));
			}
		}
		dir = new File(home,"report");
		if(dir.exists()) {
			JSONObject.Utility.dictsFromJSON(dir,reports, false);
		}
		dir = new File(home,"query");
		if(dir.exists()) {
			JSONObject.Utility.dictsFromJSON(dir,queries, false);
		}
	}



	public String reportClass() {
		if(currReport == item)
			return "selection";
		return "ungerade";
	}

	public WOActionResults selectReport() {
		if(currReport == item) {
			currReport = null;
			return null;
		}
		currReport = (NSMutableDictionary)item;
		Object params = currReport.valueForKey("queryParams");
		if(params instanceof JSONArray) {
			params = JSONObject.Utility.arrayFromJSON((JSONArray)params, true);
			currReport.setObjectForKey(params, "queryParams");
		}
		return null;
	}

	public String schoolClass() {
		if(currSchool == item)
			return "selection";
		return "gerade";
	}

	public WOActionResults selectSchool() {
		if(currSchool == item) {
			currSchool = null;
			return null;
		}
		currSchool = (NSMutableDictionary)item;
		return null;
	}
	
	public WOActionResults sendReport() {
		File responseDir = new File(home,"response");
		if(!responseDir.exists())
			responseDir.mkdir();
		Date date = new Date();
		String queryID = idFormat.format(date);
		JSONObject queryJSON = new JSONObject();
		queryJSON.putOpt("queryID", queryID);
		queryJSON.putOpt("name",currReport.valueForKey("title"));
		queryJSON.putOpt("issued", dateTimeFormat.format(date));
		
		StringBuilder buf = new StringBuilder();
		buf.append(currReport.valueForKey("entity")).append('?');
		NSArray queryParams = (NSArray)currReport.valueForKey("queryParams");
		Enumeration paramsEnu = queryParams.objectEnumerator();
		try {
			while (paramsEnu.hasMoreElements()) {
				NSDictionary param = (NSDictionary) paramsEnu.nextElement();
				String key = (String)param.valueForKey("param");
				String value = (String) paramsDict.valueForKey(key);
				if(value == null)
					continue;
				value = URLEncoder.encode(value, WOMessage.defaultURLEncoding());
				buf.append('&').append(key).append('=').append(value);
			}
			JSONObject presetParams = (JSONObject) currReport.valueForKey("presetParams");
			Iterator iter = presetParams.keys();
			while (iter.hasNext()) {
				String key = (String) iter.next();
				String value = presetParams.getString(key);
				value = URLEncoder.encode(value, WOMessage.defaultURLEncoding());
				buf.append('&').append(key).append('=').append(value);
			}
		} catch (UnsupportedEncodingException e) {
			throw new NSForwardException(e);
		}
		String queryString = buf.toString();
		JSONObject bySchool = new JSONObject();
		queryJSON.put("results", bySchool);
		try {
			if(schools.count() > 0) {
				Enumeration enu = schools.objectEnumerator();
				while (enu.hasMoreElements()) {
					NSMutableDictionary scl = (NSMutableDictionary) enu.nextElement();
					if(!Various.boolForObject(scl.valueForKey("selected")))
						continue;
					sendRequestToSchool(scl, queryString, queryID, responseDir, bySchool);
				}
			} else {
				sendRequestToSchool(currSchool, queryString, queryID, responseDir, bySchool);
			}
			responseDir = new File(home,"query");
			File resultFile = new File(responseDir,queryID + ".json");
			FileWriter writer = new FileWriter(resultFile);
			queryJSON.write(writer,2,0);
			writer.close();
			queries.insertObjectAtIndex(JSONObject.Utility.dictFromJSON(queryJSON, false), 0);
		} catch (IOException e) {
			throw new NSForwardException(e);
		}
		
		return null;
	}
	
	protected Object sendRequestToSchool(NSMutableDictionary scl, String queryString, 
			String queryID, File responseDir, JSONObject bySchool) throws IOException {
		String schoolID = (String)scl.valueForKey("schoolID");
		URL url = new URL((String)scl.valueForKey("serviceURL"));
		int port = url.getPort();
		if(port < 0)
			port = url.getDefaultPort();
		WOHTTPConnection http = new WOHTTPConnection(url.getHost(), port);
		StringBuilder buf = new StringBuilder(url.toString());
		if(buf.charAt(buf.length() -1) != '/')
			buf.append('/');
		buf.append(queryString).append("&schoolID=").append(schoolID);

		Object result = QUERY_ISSUED;
		if(bySchool != null)
			bySchool.put(schoolID, QUERY_ISSUED);
		WORequest request = new WORequest(
				"GET", buf.toString(), "HTTP/1.1", null, null, null);
		http.sendRequest(request);
		WOResponse response = http.readResponse();
		result = dateTimeFormat.format(new Date());
		if(bySchool != null)
			bySchool.put(schoolID, result);
		File schoolDir = new File(responseDir,schoolID);
		if(!schoolDir.exists())
			schoolDir.mkdirs();
		File resultFile = new File(schoolDir,queryID + ".xml");
		FileOutputStream fileOutputStream = new FileOutputStream(resultFile);
		response.content().writeToStream(fileOutputStream);
		fileOutputStream.close();
		return result;
	}

	public String paramValue() {
		if(item == null || paramsDict == null)
			return null;
		String key = (String)NSKeyValueCoding.Utility.valueForKey(item, "param");
		Object value = paramsDict.valueForKey(key);
		if(value == null || value instanceof String)
			return (String)value;
		return value.toString();
	}

	public void setParamValue(String paramValue) {
		if(item == null || paramsDict == null)
			return;
		String key = (String)NSKeyValueCoding.Utility.valueForKey(item, "param");
		paramsDict.takeValueForKey(paramValue, key);
	}
/*
	public NSArray querySchools() {
		if(query == null || currSchool != null)
			return null;
		JSONObject results = (JSONObject)query.valueForKey("results");
		if(results == null || results.length() == 0)
			return null;
		NSMutableArray schoolResults = new NSMutableArray(results.length());
		Iterator iter = results.keys();
		while (iter.hasNext()) {
			String key = (String) iter.next();
			NSDictionary sclDict = (NSDictionary)schoolByID.objectForKey(key);
//			sclDict = sclDict.mutableClone();
			sclDict.takeValueForKey(results.get(key), "queryResult");
			schoolResults.addObject(sclDict);
		}
		return schoolResults;
	}*/
	
	public String cachedQueryResult() {
		Object result = tmpDict.valueForKey("queryResult");
		if(result == null) {
			result = queryResult();
			if(result == null)
				result = NullValue;
			tmpDict.takeValueForKey(result, "queryResult");
		}
		if(result instanceof String)
			return (String)result;
		return null;
	}
	
	protected static final String[] resDecode = new String[] {
		"Запрос отправлен","Ответ получен","Ожидание доступности","Время ожидания превышено"};
	public String queryResult() {
		if(query == null)
			return null;
		JSONObject results = (JSONObject)query.valueForKey("results");
		if(results == null || results.length() == 0)
			return null;
		String schoolID = null;
		if(currSchool != null) {
			schoolID = (String)currSchool.valueForKey("schoolID");
		} else if(item instanceof NSDictionary) {
			schoolID = (String)((NSDictionary)item).valueForKey("schoolID");
		} else {
			Iterator iter = results.keys();
			int count = 0;
			while (iter.hasNext()) {
				String key = (String) iter.next();
				if(results.get(key) instanceof String)
					count++;
			}
			if(count == 0)
				return "&oslash;";
			if(count == results.length())
				return resDecode[1];
			StringBuilder buf = new StringBuilder(8);
			buf.append(count).append(" / ").append(results.length());
		}
		if(schoolID == null)
			return null;
		Object res = results.opt(schoolID);
		if(res == null)
			return null;
//		if(res instanceof String)
//			return (String)res;
		if(res instanceof Number)
			return resDecode[((Number)res).intValue()];
		return res.toString();
	}

	public void setItem(Object item) {
		this.item = item;
		tmpDict.removeAllObjects();
	}
	
	public static Integer eduYearForDate(Date date) {
		Calendar gcal = Calendar.getInstance();
		if(date != null)
			gcal.setTime(date);
		int year = gcal.get(Calendar.YEAR);
		int month = gcal.get(Calendar.MONTH);
		int newYearMonth = SettingsReader.intForKeyPath("edu.newYearMonth",Calendar.JULY);
		if(month < newYearMonth) {
			 year--;
		} else if (month == newYearMonth){
			int newYearDay = SettingsReader.intForKeyPath("edu.newYearDay",1);
			if (gcal.get(Calendar.DAY_OF_MONTH) < newYearDay)
				year--;
		}
		return new Integer(year);
	}

	public WOActionResults openXML() {
		WOResponse response = application().createResponseInContext(context()); 
		if(query != null && (item != null || currSchool != null)) {
			NSMutableDictionary scl = currSchool;
			if(currSchool == null)
				scl = (NSMutableDictionary)item;
			File toLoad = new File(home,"response");
			String id = (String)scl.valueForKey("schoolID");
			toLoad = new File(toLoad,id);
			id = (String)query.valueForKey("queryID");
			toLoad = new File(toLoad,id + ".xml");
			if(toLoad.exists()) {
				try {
					response.setContentStream(new FileInputStream(toLoad),4096,toLoad.length());
					String conType = application().resourceManager().
							contentTypeForResourceNamed(toLoad.getName());
					response.setHeader(conType,"Content-Type");
				} catch (FileNotFoundException e) {
					throw new NSForwardException(e);
				}
			}
		}
		response.disableClientCaching();
		return response;
	}
}