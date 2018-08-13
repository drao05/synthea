package org.mitre.synthea.helpers;

import java.util.Map;
import java.util.TreeMap;

import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.Year;

/**
 * Helper class to enable creating time series that obey some specific rule.
 * We use the jfree TimeSeries implementation (http://www.jfree.org/jfreechart/api/javadoc/org/jfree/data/time/TimeSeries.html.)
 * Currently supports stepwise and constant time series.
 * @author JLISTER
 *
 */

public class TimeSeriesUtils {
  
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
   * @param steps Mapping from start of a step to the value over the step. The step ends when the next key is reached.
   * @param end End of the time series.
   * @return A time series representing a stepwise function.
   */
  
  public static TimeSeries stepSeries(String name, Map<Integer, Double> steps, int end) {
    TreeMap<Integer, Double> sortedMap = new TreeMap<>(steps);
    int start = sortedMap.firstKey();
    TimeSeries newSeries = new TimeSeries(name);
    for (int i = start; i < end; i++) {
      newSeries.add(new Year(i), sortedMap.get(sortedMap.floorKey(i)));
    }
    return newSeries;
  }
}
