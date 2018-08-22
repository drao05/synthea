package org.mitre.synthea.world.geography.demographics;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of DemographicsLoader for default Synthea data.
 * 
 * @author JLISTER
 *
 */

public class DefaultDemographicsLoader extends DemographicsLoader {

  public DefaultDemographicsLoader() {
    super(CSV_AGE_GROUPS, CSV_RACES, CSV_INCOMES, CSV_EDUCATIONS, CSV_SEXES, CSV_GEOGRAPHY, ESTIMATE_HEADER);
  }

  /**
   * The index of the entry in this list + 1 == the column header in the CSV for
   * that age group. For example, age range 0-4 is stored in the CSV with column
   * header "1".
   */
  private static final List<String> CSV_AGE_GROUPS = Arrays.asList("0..4", "5..9", "10..14", "15..19", "20..24",
      "25..29", "30..34", "35..39", "40..44", "45..49", "50..54", "55..59", "60..64", "65..69", "70..74", "75..79",
      "80..84", "85..110");

  private static final List<String> CSV_RACES = Arrays.asList("WHITE", "HISPANIC", "BLACK", "ASIAN", "NATIVE", "OTHER");

  private static final List<String> CSV_INCOMES = Arrays.asList("00..10", "10..15", "15..25", "25..35", "35..50",
      "50..75", "75..100", "100..150", "150..200", "200..999");

  private static final List<String> CSV_EDUCATIONS = Arrays.asList("LESS_THAN_HS", "HS_DEGREE", "SOME_COLLEGE",
      "BS_DEGREE");
  
  private static final List<String> CSV_SEXES = Arrays.asList("TOT_MALE", "TOT_FEMALE");
  
  private static final Map<String, String> CSV_GEOGRAPHY = new HashMap<String, String>();
  
  private static final String ESTIMATE_HEADER = "POPESTIMATE2015";
  
  static {
    CSV_GEOGRAPHY.put("state", "STNAME");
    CSV_GEOGRAPHY.put("city", "NAME");
    CSV_GEOGRAPHY.put("county", "CTYNAME");
  }
}
