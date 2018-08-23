package org.mitre.synthea.engine;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.Year;

/**
 * Class for global simulation attributes that interact with the Module system.
 * The current use case is to inject time-based attribute values into a Person.
 * Implemented as a singleton.
 * 
 * If we like this pattern enough we can add nicer object oriented methods with
 * nice encapsulation, etc. For now we'll just make the maps public.
 * 
 * We may also have concurrency issues, but as long as fields are only
 * set once this shouldn't be an issue.
 * @author JLISTER
 *
 */

public class GlobalAttributes {
  
  public Map<String, Object> globalConsts;
  public Map<String, TimeSeries> globalTimeBasedAttrs;
  
  private static GlobalAttributes instance = null;
  
  private GlobalAttributes() {
    globalConsts = new HashMap<>();
    globalTimeBasedAttrs = new HashMap<>();
  }
  
  /**
   * Get the instance of global attributes.
   * @return The instance of global attributes for the simulation.
   */
  
  public static GlobalAttributes attrs() {
    if (instance == null) {
      instance = new GlobalAttributes();
    }
    return instance;
  }
  
  /**
   * Function to retrieve global attributes from a time series at a specific time.
   * These should be attributes in themselves, and not functions of other
   * attributes.
   * @param t The time at which to read the global attributes.
   */
  
  public Map<String, Object> getAttrsAtTime(long t) {
    Map<String, Object> attrs = new HashMap<>();
    for (String attr: globalTimeBasedAttrs.keySet()) {
      attrs.put(attr, globalTimeBasedAttrs.get(attr).getDataItem(new Year(new Date(t))).getValue());
    }
    return attrs;
  }

  
}
