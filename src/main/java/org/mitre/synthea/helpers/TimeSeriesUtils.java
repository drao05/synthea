package org.mitre.synthea.helpers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.Year;

/**
 * Helper class to enable creating time series that obey some specific rule.
 * We use the jfree TimeSeries implementation (http://www.jfree.org/jfreechart/api/javadoc/org/jfree/data/time/TimeSeries.html.)
 * Currently supports stepwise, piecewise, and constant time series.
 * @author JLISTER
 *
 */

public class TimeSeriesUtils {
  
  public static TimeSeries series(String name, String steps, int end, String type) {
    switch(type) {
      case "step":
        return stepSeries(name, steps, end);
      case "piecewise":
        return piecewiseSeries(name, steps, end);
      default:
        throw new IllegalArgumentException("Choose step or piecewise time series");
    }
  }
  
  /**
   * Return a time series with a constant value. For now, let's use yearly series.
   * @param name - name of the series
   * @param start - int year representing the start of the series (inclusive)
   * @param end - int year representing the end of the series
   * @param val - The value for the entire time series
   * @return A time series which spans from start to end and has the constant
   *         value val.
   */
  public static TimeSeries constantSeries(String name, int start, int end, double val) {
    TimeSeries newSeries = new TimeSeries(name);
    // Loop through from start to end
    Year currentYear = new Year(start);
    do {
      newSeries.add(currentYear, val);
      currentYear = (Year) currentYear.next();
    } while(currentYear.getYear() < end);
    return newSeries;
  }
  
  /**
   * Create a stepwise time series. For now, we use yearly series.
   * @param name Name of the series
   * @param steps Mapping from start of a step to the value over the step.
   *              The step ends when the next key is reached.
   * @param end End of the time series.
   * @return A time series representing a stepwise function.
   */
  
  public static TimeSeries stepSeries(String name, Map<Integer, Double> steps, int end) {
    TreeMap<Integer, Double> sortedMap = new TreeMap<>(steps);
    int start = sortedMap.firstKey();
    TimeSeries newSeries = new TimeSeries(name);
    for (int i = start; i < end; i++) {
      newSeries.add(new Year(i), sortedMap.floorEntry(i).getValue());
    }
    return newSeries;
  }
  
  /**
   * Convenience method for generating a step series from a string.
   * @param name Name of the series
   * @param steps String representing a mapping from start of a step to the value over the step.
   *              The step ends when the next key is reached.
   * @param end End of the time series.
   * @return A time series representing a stepwise function.
   * @throws IllegalArgumentException If a malformed time series string is submitted
   */
  public static TimeSeries stepSeries(String name, String steps, int end) throws IllegalArgumentException {
    return stepSeries(name, stringToPoints(steps), end);
  }
  
  /**
   * Create a piecewise linear ("ramped") time series.
   * @param name Name of the series
   * @param steps Mapping from start of a piecewise segment to the value at the start of the step.
   *              The step ends when the next key is reached.
   * @param end End of the time series.
   * @return A time series representing a stepwise function.
   */
  
  public static TimeSeries piecewiseSeries(String name, Map<Integer, Double> steps, int end) {
    TreeMap<Integer, Double> sortedMap = new TreeMap<>(steps);
    int start = sortedMap.firstKey();
    TimeSeries newSeries = new TimeSeries(name);
    for (int i = start; i < end; i++) {
      Entry<Integer, Double> leftBound = sortedMap.floorEntry(i),
          rightBound = sortedMap.ceilingEntry(i);
      if (rightBound != null) {
        double slope =
            (rightBound.getValue() - leftBound.getValue())/(rightBound.getKey() - leftBound.getKey());
        double dt = i - leftBound.getKey();
        newSeries.add(new Year(i), leftBound.getValue() +(dt*slope));
      } else { // if there are no greater keys, just use the floor value
        newSeries.add(new Year(i), leftBound.getValue());
      }
    }
    return newSeries;
  }
  
  /**
   * Convenience method for generating a piece series from a string.
   * @param name Name of the series
   * @param steps Mapping from start of a piecewise segment to the value at the start of the step.
   *              The step ends when the next key is reached.
   * @param end End of the time series.
   * @return A time series representing a stepwise function.
   * @throws IllegalArgumentException If a malformed time series string is submitted
   */
  public static TimeSeries piecewiseSeries(String name, String steps, int end) throws IllegalArgumentException {
    return piecewiseSeries(name, stringToPoints(steps), end);
  }
  
  /**
   * Convert a string encoding year -> value pairs to a series of points expressed as a Map.
   * Intended to be used with one of the time series construction functions above.
   * @param str String representing a series of points in time with associated values.
   *            Should be encoded as year:value,year:value,...
   * @return A Map representing this series.
   * @throws Exception 
   */
  public static Map<Integer, Double> stringToPoints(String str) throws IllegalArgumentException {
    List<String> pointList = Arrays.asList(str.split(","));
    Map<Integer, Double> pointMap = new HashMap<>();
    for (String p : pointList) {
      String yearValue[] = p.split(":");
      if (yearValue.length != 2) {
        throw new IllegalArgumentException("Invalid time series notation in " + str);
      } else {
        pointMap.put(Integer.parseInt(yearValue[0]), Double.parseDouble(yearValue[1]));
      }
    }
    return pointMap;
  }
}