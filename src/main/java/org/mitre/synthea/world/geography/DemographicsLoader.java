package org.mitre.synthea.world.geography;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for loading demographics
 * @author JLISTER
 *
 */

public class DemographicsLoader {
  
  private DemographicsOptions options; 
  
  public DemographicsLoader(List<String> ag, List<String> r, List<String> i, List<String> e, String estHeader) {
    options = new DemographicsOptions(ag, r, i, e, estHeader);
  }
  
  public Demographics csvLineToDemographics(Map<String,String> line) {
    Demographics d = new Demographics();
    
    d.population = Double.valueOf(line.get(options.getPopEstHeader())).longValue();
    // some .0's seem to sneak in there and break Long.valueOf
    
    d.city = line.get("NAME");
    d.state = line.get("STNAME");
    d.county = line.get("CTYNAME");
    
    d.ages = new HashMap<String, Double>();
    
    int i = 1;
    for (String ageGroup : options.getAgeGroups()) {
      String csvHeader = Integer.toString(i++);
      double percentage = Double.parseDouble(line.get(csvHeader));
      d.ages.put(ageGroup, percentage);
    }
    
    d.gender = new HashMap<String, Double>();
    d.gender.put("male", Double.parseDouble(line.get("TOT_MALE")));
    d.gender.put("female", Double.parseDouble(line.get("TOT_FEMALE")));
    
    d.race = new HashMap<String, Double>();
    for (String race : options.getRaces()) {
      double percentage = Double.parseDouble(line.get(race));
      d.race.put(race.toLowerCase(), percentage);
    }
    
    d.income = new HashMap<String, Double>();
    for (String income : options.getIncomes()) {
      String incomeString = line.get(income);
      if (incomeString.isEmpty()) {
        d.income.put(income, 0.01); // dummy value, has to be non-zero
      } else {
        double percentage = Double.parseDouble(incomeString);
        d.income.put(income, percentage);
      }
    }
    
    d.education = new HashMap<String, Double>();
    for (String education : options.getEducations()) {
      String educationString = line.get(education);
      if (educationString.isEmpty()) {
        d.education.put(education.toLowerCase(), 0.01); // dummy value, has to be non-zero
      } else {
        double percentage = Double.parseDouble(educationString);
        d.education.put(education.toLowerCase(), percentage);
      }
    }
    
    return d;
  }
}
