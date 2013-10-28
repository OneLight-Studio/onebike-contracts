package com.onelightstudio.onebike;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class ContractsGenerator {
	
	private static final String CONTRACTS_PROVIDER_JCD = "JCDecaux";
	private static final String CONTRACTS_PROVIDER_CITYBIKES = "CityBikes";
	private static final String JCD_API_KEY = "e774968643aee3788d9b83be4651ba671aba7611";
	private static final String JCD_CONTRACTS_URL = "https://api.jcdecaux.com/vls/v1/contracts?apiKey=" + JCD_API_KEY;
	private static final String JCD_CONTRACT_URL = "https://api.jcdecaux.com/vls/v1/stations?apiKey=" + JCD_API_KEY + "&contract=";
    private static final String CITYBIKES_URL = "http://api.citybik.es/networks.json";
    private static final String GOOGLE_URL = "http://maps.googleapis.com/maps/api/geocode/json?sensor=false";
    private static JSONArray outContracts;

    public static void main(String[] args) {
        //Fill contracts
    	outContracts = new JSONArray();
        try {
        	System.out.println("JCDecaux:\n");
            parseContracts(getJSONForUrl(JCD_CONTRACTS_URL), CONTRACTS_PROVIDER_JCD);
            
            System.out.println("\nCityBikes:\n");
            parseContracts(getJSONForUrl(CITYBIKES_URL), CONTRACTS_PROVIDER_CITYBIKES);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        //Write the json file
        try {
        	FileWriter writer = new FileWriter("contracts.json");
            writer.write(outContracts.toJSONString().replace("\\/", "/"));
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void parseContracts(JSONArray contracts, String provider) throws Exception {
    	for (int i = 0; i < contracts.size(); i++) {
            JSONObject contract = (JSONObject) contracts.get(i);
            
            //Contract properties
            String city = (String) contract.get("name");
        	double cityLat = 0;
        	double cityLng = 0;
        	int radius;
        	String url = "";
        	JSONArray stations = null;
            
        	//Wait a bit cause google doesn't allow more than 10 request per sec
            try {
                Thread.sleep(100);;
            } catch (InterruptedException e) {}
        	
            switch(provider) {
            case CONTRACTS_PROVIDER_JCD:String country = (String) contract.get("country_code");
                JSONObject address = (JSONObject) JSONValue.parse(new BufferedReader(new InputStreamReader(new URL(GOOGLE_URL + "&address=" + URLEncoder.encode(city + "," + country, "UTF8")).openStream())));
                JSONArray results = (JSONArray) address.get("results");
                if (results.isEmpty()) {
                    System.err.println(city + " not found");
                    break;
                }
                
                //Set properties
                JSONObject firstResult = (JSONObject) results.get(0);
                JSONObject geometry = (JSONObject) firstResult.get("geometry");
                JSONObject cityLocation = (JSONObject) geometry.get("location");
                cityLat = (Double) cityLocation.get("lat");
                cityLng = (Double) cityLocation.get("lng");
                url = JCD_CONTRACT_URL.replace("apiKey=" + JCD_API_KEY + "&", "") + city;
                
                //Get stations for this contract
                stations = (JSONArray) JSONValue.parse(new BufferedReader(new InputStreamReader(new URL(JCD_CONTRACT_URL + city).openStream())));
            	break;
            case CONTRACTS_PROVIDER_CITYBIKES:
            	cityLat = ((Long) contract.get("lat")).doubleValue() / 1E6;
            	cityLng = ((Long) contract.get("lng")).doubleValue() / 1E6;
            	url = (String) contract.get("url");
            	city = getCitynameForLatLng(cityLat, cityLng, city);
            	
            	//Get stations for this contract
                stations = (JSONArray) JSONValue.parse(new BufferedReader(new InputStreamReader(new URL(url).openStream())));
            	break;
            }
            
            if (!isContractAlreadyHandled(cityLat, cityLng) && stations != null) {
            	radius = getRadiusForStations(stations, cityLat, cityLng, provider);
                if (radius > 0) {
                	//Set properties
                	JSONObject outContract = new JSONObject();
                    outContract.put("name", city);
                    outContract.put("lat", cityLat);
                    outContract.put("lng", cityLng);
                    outContract.put("radius", radius);
                    outContract.put("url", url);
                    outContract.put("provider", provider);
                    outContracts.add(outContract);
                    System.out.println(outContract.toJSONString().replace("\\/", "/"));                
                }
        	}
        }
    }
    
    private static boolean isContractAlreadyHandled(double newContractLat, double newContractLng) {
    	double contractLat = 0;
		double contractLng = 0;
    	
    	for (int i = 0; i < outContracts.size(); i++) {
    		JSONObject contract = (JSONObject) outContracts.get(i);
    		
    		contractLat = (Double) contract.get("lat");
    		contractLng = (Double) contract.get("lng");
    		if (getDistance(contractLat, contractLng, newContractLat, newContractLng) < ((Integer) contract.get("radius")).doubleValue())  {
    			return true;
    		}
    	}
    	
    	return false;
    }
    
    private static JSONArray getJSONForUrl(String url) throws Exception {
    	return (JSONArray) JSONValue.parse(new BufferedReader(new InputStreamReader(new URL(url).openStream())));
    }
    
    private static String getCitynameForLatLng(double cityLat, double cityLng, String defaultName) throws Exception {
    	JSONObject cityName = (JSONObject) JSONValue.parse(new BufferedReader(new InputStreamReader(new URL(GOOGLE_URL + "&latlng=" + cityLat + "," + cityLng).openStream())));
    	JSONArray cityNameRes = (JSONArray) cityName.get("results");
        if (!cityNameRes.isEmpty()) {
            JSONArray cityNameParts = (JSONArray) ((JSONObject) cityNameRes.get(0)).get("address_components");
            for (int j = 0; j < cityNameParts.size(); j++) {
            	JSONObject part = (JSONObject) cityNameParts.get(j);
            	JSONArray types = (JSONArray) part.get("types");
            	if (types.contains("locality")) {
            		return (String) part.get("short_name");
            	}
            }
        }
        
        return defaultName;
    }
    
    private static int getRadiusForStations(JSONArray stations, double cityLat, double cityLng, String provider) {
        if (stations.isEmpty()) {
            System.err.println("No station found for " + cityLat + " " + cityLng);
            return 0;
        } else {
        	int stationErrorCount = 0;
        	int stationZeroZeroCount = 0;
        	
            double maxDistance = 0;
            for (int j = 0; j < stations.size(); j++) {
                JSONObject station = (JSONObject) stations.get(j);
                double stationLat = 0;
                double stationLng = 0;
                
                try {
                	switch(provider) {
                    case CONTRACTS_PROVIDER_JCD:
                    	JSONObject stationLocation = (JSONObject) station.get("position");
                    	stationLat = (Double) stationLocation.get("lat");
    	                stationLng = (Double) stationLocation.get("lng");
                    	break;
                    case CONTRACTS_PROVIDER_CITYBIKES:
		                stationLat = ((Long) station.get("lat")).doubleValue() / 1E6;
		                stationLng = ((Long) station.get("lng")).doubleValue() / 1E6;
		                break;
                	}
                	
	                if (stationLat == 0 && stationLng == 0) {
	                	stationZeroZeroCount++;
	                	continue;
	                }
                }
                catch (Exception e) {
                	stationErrorCount++;
                	continue;
                }
                
                double distanceFromCity = getDistance(cityLat, cityLng, stationLat, stationLng);
                if (distanceFromCity > 20000) {
                	String stationName = (String) station.get("name");
                	System.err.println("Station " + stationName + " seems way too far (" + (int)distanceFromCity + " meters)");
                	continue;
                }
                
                if (distanceFromCity > maxDistance) {
                	maxDistance = distanceFromCity;
                }
            }
            
            if (stationErrorCount > 0) {
            	System.err.println("Stations errors : " + stationErrorCount + "/" + stations.size());
            }            
            if (stationZeroZeroCount > 0) {
            	System.err.println("Stations with lat/lng at 0 : " + stationZeroZeroCount + "/" + stations.size());
            }
            
            return ((int)(maxDistance / 1000) + 1) * 1000;
        }
    }

    private static double getDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.sin(dLng / 2) * Math.sin(dLng / 2) * Math.cos(radLat1) * Math.cos(radLat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000 * c;
    }
}
