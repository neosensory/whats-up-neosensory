package com.neosensory.whatsup;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

public class Satellite {
  private int noradId;
  private String intlDesignator;
  private String tle1;
  private String tle2;
  private String name;
  private double latitude;
  private double longitude;
  private double altitude;
  private Boolean hasTle;
  Marker satelliteMarker;
  private Boolean hasMarker;
  private double distanceToUser;
  private double alphaDistanceToUser;
  private double lastDistanceToUser;
  private double userBearing;
  private Boolean userBearingSet;
  private Boolean distanceToUserSet;
  private Boolean
      locationUpdatedCalled; // used to only make the satellite active once we've started updating
  // its position via TLE (to avoid jumping)
  private Boolean headedTowardsUser;
  private Boolean readyToRemove;
  private Boolean outsideUserRange;

  /**
   * Satellite constructor for keeping track of a satellite and its UI element
   *
   * @param noradId_ int NORAD ID for the satellite
   * @param intlDesignator_ String unique int'l de
   * @param name_ The satellite's name (via n2yo.com)
   * @param lat The satellite's starting latitude
   * @param lon The satellite's starting longitude
   * @param alt The satellite's starting altitude
   */
  public Satellite(
      int noradId_, String intlDesignator_, String name_, double lat, double lon, double alt) {
    noradId = noradId_;
    intlDesignator = intlDesignator_;
    name = name_;
    latitude = lat;
    longitude = lon;
    altitude = alt;
    hasTle = false;
    hasMarker = false;
    satelliteMarker = null;
    distanceToUserSet = false;
    locationUpdatedCalled = false;
    readyToRemove = false;
    outsideUserRange = false;
    userBearingSet = false;
    headedTowardsUser = true;
  }

  /**
   * Check to the the bearing from the user to the satellite has been set yet
   *
   * @return whether or not the bearing has been set
   */
  public Boolean getUserBearingSet() {
    return userBearingSet;
  }

  /**
   * Set the bearing from the user to the satellite
   *
   * @param bearing - the bearing (in degrees)
   */
  public void setUserBearing(double bearing) {
    userBearing = bearing;
    userBearingSet = true;
  }

  /**
   * Get the bearing from the user to the satellite (in degrees)
   *
   * @return the bearing (if it's set), or -1 if it's not
   */
  public double getUserBearing() {
    if (userBearingSet) {
      return userBearing;
    } else {
      return -1;
    }
  }

  /**
   * Set whether or not the satellite is outside a defined distance from the user
   *
   * @param outsideUserRange_
   */
  public void setOutsideUserRange(Boolean outsideUserRange_) {
    outsideUserRange = outsideUserRange_;
  }

  /**
   * Get whether or not the satellite is outside a defined distance from the user
   *
   * @return
   */
  public Boolean getOutsideUserRange() {
    return outsideUserRange;
  }

  /**
   * Remove the Google Maps marker associated with the satellite. This needs to happen on a UI
   * thread.
   */
  public void removeMarker() {
    satelliteMarker.remove();
    hasMarker = false;
    readyToRemove = true;
  }

  /**
   * Check to see if we can delete the object (determined by if we've already deleted the associated
   * marker)
   *
   * @return true if ready to remove the object
   */
  public Boolean getReadyToRemoveSatellite() {
    return readyToRemove;
  }

  /**
   * Check to see if a TLE has been used to update this satellite's location. The initial location
   * is provided by the n2yo.com "What's Up" call and there is usually a big jump in position
   * between the initial position and when "real-time" tracking begins
   *
   * @return true if a TLE has been used to update the satellite's location
   */
  public Boolean getTleUsedforLocation() {
    return locationUpdatedCalled;
  }

  /**
   * Map the distance from user to satellite on an inverse 0-1 scale (for setting the marker
   * // alpha). 0 = further, 1 = closer. Also determine based on previous calculation if the
   * satellite is headed to/away from user
   *
   * @param distance the distance from the user to the satellite
   * @param maxDistance the maximum distance that defines where the satellite should be fully faded
   *     out
   */
  public void setDistanceToUser(double distance, double maxDistance) {
      distanceToUser = distance;
      alphaDistanceToUser =
          1 - Utilities.getLinearMap((float) distance, 0, (float) maxDistance, 0, 1, true);
    if (distanceToUserSet) {
      double direction = distanceToUser - lastDistanceToUser;
      headedTowardsUser = (direction <= 0);
    }
    lastDistanceToUser = distanceToUser;
    distanceToUserSet = true;
  }

  /**
   * Get whether or not the satellite is getting closer to the user
   *
   * @return true if the satellite is moving towards the user
   */
  public Boolean getHeadedToUser() {
    return headedTowardsUser;
  }

  /**
   * Get the distance to user if it's been set
   *
   * @return the distance (in km) or -1 if this has not yet been set
   */
  public double getDistanceToUser() {
    if (distanceToUserSet) {
      return distanceToUser;
    } else {
      return -1;
    }
  }

  /**
   * Get the distance mapped to the alpha/opacity value for the marker
   *
   * @return the alpha value (on [0,1]) if the distance to the user has been set. -1 otherwise.
   */
  public float getAlphaDistanceToUser() {
    if (distanceToUserSet) {
      return (float) alphaDistanceToUser;
    } else {
      return -1;
    }
  }

  /**
   * Set the Google Maps marker to be associated with the satellite
   *
   * @param marker the Google Maps marker to assign to this object
   */
  public void setSatelliteMarker(Marker marker) {
    satelliteMarker = marker;
    hasMarker = true;
  }

  /**
   * Check to see if the Satellite has a Google Maps marker attached to it
   *
   * @return true if it has a Google Maps marker attached to it
   */
  public Boolean getHasMarker() {
    return hasMarker;
  }

  /**
   * Obtain the attached Google Maps marker
   *
   * @return the Google Maps marker
   */
  public Marker getSatelliteMarker() {
    return satelliteMarker;
  }

  /**
   * Update the position of the attached Google Maps marker based on the current set
   * latitude+longitude coordinates
   */
  public void updateMarkerPosition() {
    satelliteMarker.setPosition(new LatLng(latitude, longitude));
  }

  /**
   * Set the alpha (transparency) of the attached Google Maps Marker
   *
   * @param alpha transparency value on [0,1]
   */
  public void setAlpha(float alpha) {
    satelliteMarker.setAlpha(alpha);
  }

  /**
   * Get the Norad ID for the satellite
   *
   * @return the Norad ID
   */
  public int getNoradID() {
    return noradId;
  }

  /**
   * Get the International Designator for the satellite
   *
   * @return the International Designator
   */
  public String getIntlDesignator() {
    return intlDesignator;
  }

  /**
   * Get the satellite's name
   *
   * @return the satellite's name
   */
  public String getName() {
    return name;
  }

  /**
   * Get the two line element (TLE) set associated with the satellite if one has been assigned
   *
   * @return String array for each TLE line
   */
  public String[] getTles() {
    if (hasTle) {
      String[] tles = {tle1, tle2};
      return tles;
    } else {
      String[] tles = {"", ""};
      return tles;
    }
  }

  /**
   * Set the two line element (TLE) set for the satellite
   *
   * @param tle1_ TLE line 1
   * @param tle2_ TLE line 2
   */
  public void setTles(String tle1_, String tle2_) {
    tle1 = tle1_;
    tle2 = tle2_;
    hasTle = true;
  }

  /**
   * Check to see if the satellite has a TLE associated with it
   *
   * @return true if the satellite has an associated TLE
   */
  public Boolean getHasTle() {
    return hasTle;
  }

  /**
   * Set the latitude/longitude/altitude for the satellite
   *
   * @param lat latitude (degrees)
   * @param lon longitude (degrees)
   * @param alt altitude (meters above sea level)
   */
  public void setLla(double lat, double lon, double alt) {
    latitude = lat;
    longitude = lon;
    altitude = alt;
    locationUpdatedCalled = true;
  }

  /**
   * Get the latitude/longitude/altitude for the satellite
   *
   * @return a double array containing latitude (degrees), longitude (degrees), and altitude (meters
   *     above sea level)
   */
  public double[] getLla() {
    double[] lla = {latitude, longitude, altitude};
    return lla;
  }
}
