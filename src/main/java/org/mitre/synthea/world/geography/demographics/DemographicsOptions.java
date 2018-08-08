package org.mitre.synthea.world.geography.demographics;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * Class representing options for factors that can be included when building demographics,
 * as well as the specific headers to be used when retrieving the information from the
 * CSVs.
 * Currently doesn't do anything special, just contains the data and exposes
 * some fields in a nicer way. Perhaps later we could leverage the "has*" functions
 * or write additional code to refine this.
 * @author JLISTER
 *
 */

public class DemographicsOptions {
  private final List<String> csvAgeGroups;
  private final List<String> csvRaces;
  private final List<String> csvIncomes;
  private final List<String> csvEducations;
  private final List<String> csvSexes;
  private final Map<String, String> csvGeography;
  private final String popEstHeader;

  public DemographicsOptions(List<String> ag, List<String> r, List<String> i, List<String> e, List<String> s, Map<String, String> geo, String estHeader) {
    csvAgeGroups = ag;
    csvRaces = r;
    csvIncomes = i;
    csvEducations = e;
    csvSexes = s;
    csvGeography = ImmutableMap.copyOf(geo);
    popEstHeader = estHeader;
  }

  public boolean hasAgeGroups() {
    return csvAgeGroups != null;
  }

  public boolean hasRaces() {
    return csvRaces != null;
  }

  public boolean hasIncomes() {
    return csvIncomes != null;
  }

  public boolean hasEducations() {
    return csvEducations != null;
  }
  
  public boolean hasCity() {
    return csvGeography.containsKey("city");
  }
  
  public boolean hasState() {
    return csvGeography.containsKey("state");
  }
  
  public boolean hasZip() {
    return csvGeography.containsKey("zip");
  }
  
  public boolean hasCounty() {
    return csvGeography.containsKey("county");
  }
  
  public String getCity() {
    return csvGeography.get("city");
  }
  
  public String getState() {
    return csvGeography.get("state");
  }
  
  public String getZip() {
    return csvGeography.get("zip");
  }
  
  public String getCounty() {
    return csvGeography.get("county");
  }

  public List<String> getAgeGroups() {
    return csvAgeGroups;
  }

  public List<String> getRaces() {
    return csvRaces;
  }

  public List<String> getIncomes() {
    return csvIncomes;
  }

  public List<String> getEducations() {
    return csvEducations;
  }
  
  /* TODO: maybe make this a map? Not sure what gender categories matter for medical purposes.
   * Maybe if we want to distinguish between trans people under the gender category.
   */
  
  public String getMale() {
    return csvSexes.get(0);
  }
  
  public String getFemale() {
    return csvSexes.get(1);
  }

  public String getPopEstHeader() {
    return popEstHeader;
  }
}
