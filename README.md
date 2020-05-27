# whats-up-android
Neosensory Android SDK demo app that lets you feel satellites around you. Currently the motor encoding is assigned to the nearest satellite using a vibratory illusion. The overall strength of the vibration represents how close the satellite is to you, and the specific motors that vibrate indicate the direction of the satellite.

To determine nearby satellites, we leverage the API from [n2yo.com](https://www.n2yo.com/) and built an [Android library](https://github.com/neosensory/n2yo-android-lib) that makes interacting with API through Android easy. Once we get a list of nearby satellites, we obtain a two line element (TLE) for each satellite from N2YO, which can be used to predict its current location. Once we've obtained a TLE we decode it and run its parameters through a physics model (SGP4) and produce latitude, longitude, and altitude coordinates. For this we've built a standalone Java library, [TLE Prediction Engine](https://github.com/neosensory/tle-prediction-engine) that makes this a breeze. 

## Prerequisites
To run this app you'll need to obtain both an API key for Google Maps (placed [here](https://github.com/neosensory/whats-up-neosensory/blob/master/app/src/release/res/values/google_maps_api.xml)) API key from [n2yo.com](https://www.n2yo.com/) (place it [here](https://github.com/neosensory/whats-up-neosensory/blob/master/app/src/main/res/values/other_api_keys.xml))

## Other stuff
Note: The [n2yo.com API](https://www.n2yo.com/api/) enforces a limit of 1000 requests per hour and are adamant about not abusing this. It is therefore important to be cognizant of this when setting the following parameters:
* `REFRESHSATELLITESPERIOD` the time in ms between each request to gather a list of all the satellites near the user. Each refresh is an n2yo.com "What's Up" request.
* `MAXSATELLITES` the maximum number of satellites to track at any given point in time. Each tracked satellite has an n2yo.com TLE request associate it.

The maximum possible requests per refresh is (1+`MAXSATELLITES`). If the refresh period is x seconds, then the maximum possible requests per hour is (1+`MAXSATELLITES`)*(3600/x). 

Currently, the satellites are stored in a HashTable using their NORAD ID as a key. The maximum HashTable size is `MaxSatellites.` Tracked satellites only get removed if they're out of our defined distance and moving away from the user location. Therefore, it is possible to have <= (1+`MAXSATELLITES`) N2YO API requests per refresh.
