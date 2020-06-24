package com.neosensory.whatsup;

public class Utilities {

    /**
     * Linearly map an input on [x1,y1] to an output on [x2,y2]
     * @param input the input value
     * @param in_min the minimum input value
     * @param in_max the maximum input value
     * @param out_min the minimum output value
     * @param out_max the maximum output value
     * @param constrain_output make sure the output is constrained between out_min and out_max
     * @return the input value linearly mapped to the output scale
     */
    public static float getLinearMap(float input, float in_min, float in_max, float out_min, float out_max,
                                   Boolean constrain_output) {
        if (in_max == in_min) {
            return out_min;
        }
        // Scale input range to [0, 1] and then to output range
        float output = ((input - in_min) / (in_max - in_min)) * (out_max - out_min) + out_min;
        if (constrain_output) {
            if (out_min < out_max) {
                output = Math.max(output, out_min);
                output = Math.min(output, out_max);
            } else {
                output = Math.max(output, out_max);
                output = Math.min(output, out_min);
            }
        }
        return output;
    }

    /**
     * get the distance between two latitude+longitude coordinates
     * credit: https://gist.github.com/vananth22/888ed9a22105670e7a4092bdcf0d72e4
     * @param lat1 point 1 latitude
     * @param lat2 point 2 latitude
     * @param lon1 point 1 longitude
     * @param lon2 point 2 longitude
     * @return the distance (in km) between the two latitude/longitude coordinates
     */
    public static double getDistance(double lat1, double lat2, double lon1, double lon2) {
        final int R = 6371; // Radius of the earth
        double latDistance = toRad(lat2 - lat1);
        double lonDistance = toRad(lon2 - lon1);
        double a =
                Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                        + Math.cos(toRad(lat1))
                        * Math.cos(toRad(lat2))
                        * Math.sin(lonDistance / 2)
                        * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Get the bearing from one latitude longitude point to another. If the point is directly north, this return a bearing of 0 degrees.
     * @param lat1 point 1 latitude
     * @param lat2 point 2 latitude
     * @param lon1 point 1 longitude
     * @param lon2 point 2 longitude
     * @return the bearing in degrees [0:360) where 0 = North
     */
    public static double getBearing(double lat1, double lat2, double lon1, double lon2) {
        double lat1r = toRad(lat1);
        double lat2r = toRad(lat2);
        double lon1r = toRad(lon1);
        double lon2r = toRad(lon2);
        double y = Math.sin(lon2r - lon1r) * Math.cos(lat2r);
        double x =
                Math.cos(lat1r) * Math.sin(lat2r)
                        - Math.sin(lat1r) * Math.cos(lat2r) * Math.cos(lon2r - lon1r);

        return (toDeg(Math.atan2(y, x)) + 360) % 360;
    }
    // convert degrees to radians
    private static Double toRad(Double value) {
        return value * Math.PI / 180;
    }
    // convert radians to degrees
    private static Double toDeg(Double value) {
        return value * 180 / Math.PI;
    }

}
