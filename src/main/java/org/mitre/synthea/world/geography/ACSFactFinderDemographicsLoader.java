package org.mitre.synthea.world.geography;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of DemographicsLoader to interface with ACS FactFinder data retrieved
 * for the VA Telemedicine MIP project. This is probably similar to or the same as Synthea's
 * default data, but in a different format.
 * @author JLISTER
 *
 */

public class ACSFactFinderDemographicsLoader extends DemographicsLoader {
  
  public ACSFactFinderDemographicsLoader() {
    super(CSV_AGE_GROUPS_ACS, CSV_RACES_ACS, CSV_INCOMES_ACS, CSV_EDUCATIONS_ACS,
        CSV_SEXES_ACS, CSV_GEOGRAPHY_ACS, ESTIMATE_HEADER_ACS);
  }

  private static final List<String> CSV_AGE_GROUPS_ACS = Arrays.asList("18..34", "35..54", "55..64", "65..74",
      "75..110");

  private static final List<String> CSV_RACES_ACS = Arrays.asList("WHITE", "HISPANIC", "BLACK", "ASIAN", "NATIVE", "OTHER");

  private static final List<String> CSV_INCOMES_ACS = Arrays.asList("00..11", "11..999");

  private static final List<String> CSV_EDUCATIONS_ACS = Arrays.asList("LESS_THAN_HS", "HS_DEGREE", "SOME_COLLEGE",
      "BS_DEGREE");
  
  private static final List<String> CSV_SEXES_ACS = Arrays.asList("TOT_MALE", "TOT_FEMALE");
  
  private static final Map<String, String> CSV_GEOGRAPHY_ACS = new HashMap<String, String>();
  
  private static final String ESTIMATE_HEADER_ACS = "POPESTIMATE2015";
  
  static {
    CSV_GEOGRAPHY_ACS.put("state", "STNAME");
    CSV_GEOGRAPHY_ACS.put("city", "NAME");
    CSV_GEOGRAPHY_ACS.put("county", "CTYNAME");
  }

}
