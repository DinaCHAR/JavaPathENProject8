package com.openclassrooms.tourguide.model;

import gpsUtil.location.Location;

public class NearbyAttractionInfo {
    private String attractionName;
    //private double attractionLatitude;
    //private double attractionLongitude;
    private Location locationAttraction;
    //private double userLatitude;
    //private double userLongitude;
    private Location locationUser;
    private double distanceInMiles;
    private int rewardPoints;

    public NearbyAttractionInfo(String attractionName, Location locationAttraction, Location locationUser, double distanceInMiles, int rewardPoints) {
        this.attractionName = attractionName;
        //this.attractionLatitude = attractionLatitude;
        //this.attractionLongitude = attractionLongitude;
        //this.userLatitude = userLatitude;
        //this.userLongitude = userLongitude;O
        this.locationAttraction = locationAttraction;
        this.locationUser = locationUser;
        this.distanceInMiles = distanceInMiles;
        this.rewardPoints = rewardPoints;
    }

    public Location getLocationAttraction() {
		return locationAttraction;
	}

	public void setLocationAttraction(Location locationAttraction) {
		this.locationAttraction = locationAttraction;
	}

	public Location getLocationUser() {
		return locationUser;
	}

	public void setLocationUser(Location locationUser) {
		this.locationUser = locationUser;
	}

	public String getAttractionName() {
        return attractionName;
    }


    public double getDistanceInMiles() {
        return distanceInMiles;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }
}