package org.mitre.synthea.world.geography.demographics;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Class for loading demographics
 * @author JLISTER
 *
 */

public class DemographicsLoader {
  
  private static Logger demographicsLoaderLogger = LoggerFactory.getLogger(DemographicsLoader.class);
  private DemographicsOptions options; 
  
  public DemographicsLoader(List<String> ag, List<String> r, List<String> i, List<String> e, List<String> s, String estHeader) {
    options = new DemographicsOptions(ag, r, i, e, s, estHeader);
  }
  
  /**
   * Get a Table of (State, City, Demographics), with the given restrictions on state and city.
   * 
   * @param state
   *          The state that is desired. Other states will be excluded from the results.
   * @param filename The name of the file from which demographics will be loaded.
   * @return Table of (State, City, Demographics)
   * @throws IOException
   *           if any exception occurs in reading the demographics file
   */
  
  public Table<String, String, CityStateDemographics> load(String state, String filename) {
    String csv = null;
    try {
      csv = Utilities.readResource(filename);
    } catch (IOException e) {
      demographicsLoaderLogger.error("loading csv " + filename + " failed");
      e.printStackTrace();
      return null;
    }
    
    List<? extends Map<String, String>> demographicsCsv = null;
    try {
      demographicsCsv = SimpleCSV.parse(csv);
    } catch (IOException e) {
      demographicsLoaderLogger.error("parsing csv " + filename + " failed");
      e.printStackTrace();
      return null;
    }
    
    Table<String, String, CityStateDemographics> table = HashBasedTable.create();
    
    for (Map<String,String> demographicsLine : demographicsCsv) {
      String currCity = demographicsLine.get("NAME");
      String currState = demographicsLine.get("STNAME");
      
      // for now, only allow one state at a time
      if (state != null && state.equalsIgnoreCase(currState)) {
        CityStateDemographics parsed = csvLineToDemographics(demographicsLine);
        
        table.put(currState, currCity, parsed);
      }
    }
    
    return table;
  }
  
  public CityStateDemographics csvLineToDemographics(Map<String,String> line) {
    CityStateDemographics d = new CityStateDemographics();
    
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
    d.gender.put("male", Double.parseDouble(line.get(options.getSexes().get(0))));
    d.gender.put("female", Double.parseDouble(line.get(options.getSexes().get(1))));
    
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
