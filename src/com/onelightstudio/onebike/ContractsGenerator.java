package com.onelightstudio.onebike;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class ContractsGenerator {
	
	private static final String JCD_API_KEY = "SOME_KEY";
    private static final String JCD_CONTRACTS_URL = "https://api.jcdecaux.com/vls/v1/contracts?apiKey=" + JCD_API_KEY;
    private static final String JCD_CONTRACT_URL = "https://api.jcdecaux.com/vls/v1/stations?apiKey=" + JCD_API_KEY + "&contract=";
    private static final String CITYBIKES_URL = "http://api.citybik.es/networks.json";
    private static final String GOOGLE_URL = "http://maps.googleapis.com/maps/api/geocode/json?sensor=false&address=";

    public static void main(String[] args) {
        JSONArray outContracts = new JSONArray();
        try {
        	// JCDecaux
        	System.out.println("JCDecaux:\n");
            JSONArray jcdContracts = (JSONArray) JSONValue.parse(new BufferedReader(new InputStreamReader(new URL(JCD_CONTRACTS_URL).openStream())));
            long lastGoogleCall = 0;
            for (int i = 0; i < jcdContracts.size(); i++) {
                JSONObject contract = (JSONObject) jcdContracts.get(i);
                String city = (String) contract.get("name");
                String country = (String) contract.get("country_code");
                if (lastGoogleCall > 0) {
                    long now = System.currentTimeMillis();
                    try {
                        Thread.currentThread().wait(100 - (now - lastGoogleCall));
                    } catch (InterruptedException e) {
                    }
                }
                JSONObject address = (JSONObject) JSONValue.parse(new BufferedReader(new InputStreamReader(new URL(GOOGLE_URL + URLEncoder.encode(city + "," + country, "UTF8")).openStream())));
                JSONArray results = (JSONArray) address.get("results");
                if (results.isEmpty()) {
                    System.err.println(city + " not found");
                } else {
                    JSONObject firstResult = (JSONObject) results.get(0);
                    JSONObject geometry = (JSONObject) firstResult.get("geometry");
                    JSONObject cityLocation = (JSONObject) geometry.get("location");
                    double cityLat = (Double) cityLocation.get("lat");
                    double cityLng = (Double) cityLocation.get("lng");
                    JSONArray stations = (JSONArray) JSONValue.parse(new BufferedReader(new InputStreamReader(new URL(JCD_CONTRACT_URL + city).openStream())));
                    if (stations.isEmpty()) {
                        System.err.println("No station found for " + city);
                    } else {
                        double maxDistance = 0;
                        for (int j = 0; j < stations.size(); j++) {
                            JSONObject station = (JSONObject) stations.get(j);
                            JSONObject stationLocation = (JSONObject) station.get("position");
                            double stationLat = (Double) stationLocation.get("lat");
                            double stationLng = (Double) stationLocation.get("lng");
                            if (stationLat == 0 && stationLng == 0) {
                                String stationName = (String) station.get("name");
                            	System.err.println("Station " + stationName + " located at (0,0) ignored");
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
                        int radius = ((int)(maxDistance / 1000) + 1) * 1000;
                        JSONObject outContract = new JSONObject();
                        outContract.put("name", city);
                        outContract.put("lat", cityLat);
                        outContract.put("lng", cityLng);
                        //outContract.put("radius", radius);
                        outContract.put("radius", 20000);
                        outContract.put("url", JCD_CONTRACT_URL.replace("apiKey=" + JCD_API_KEY + "&", "") + city);
                        outContracts.add(outContract);
                        System.out.println(outContract.toJSONString().replace("\\/", "/"));
                    }
                }
            }
            // CityBikes
        	System.out.println("\nCityBikes:\n");
            JSONArray ctbContracts = (JSONArray) JSONValue.parse(new BufferedReader(new InputStreamReader(new URL(CITYBIKES_URL).openStream())));
            for (int i = 0; i < ctbContracts.size(); i++) {
            	JSONObject contract = (JSONObject) ctbContracts.get(i);
            	String city = (String) contract.get("name");
            	double lat = (Long) contract.get("lat") / 1E6;
            	double lng = (Long) contract.get("lng") / 1E6;
            	int radius = ((Long) contract.get("radius")).intValue();
            	String url = (String) contract.get("url");
            	JSONObject outContract = new JSONObject();
                outContract.put("name", city);
                outContract.put("lat", lat);
                outContract.put("lng", lng);
                outContract.put("radius", radius);
                outContract.put("url", url);
                outContracts.add(outContract);
                System.out.println(outContract.toJSONString().replace("\\/", "/"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
        	FileWriter writer = new FileWriter("contracts.json");
            writer.write(outContracts.toJSONString().replace("\\/", "/"));
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
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
