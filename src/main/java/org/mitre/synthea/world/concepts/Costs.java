package org.mitre.synthea.world.concepts;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.geography.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Costs {
  // all of these are CSVs with these columns: 
  // code, min cost in $, mode cost in $, max cost in $, comments
  private static final Map<String, CostData> PROCEDURE_COSTS =
      parseCsvToMap("costs/procedures.csv");
  private static final Map<String, CostData> MEDICATION_COSTS =
      parseCsvToMap("costs/medications.csv");
  private static final Map<String, CostData> ENCOUNTER_COSTS =
      parseCsvToMap("costs/encounters.csv");
  private static final Map<String, CostData> IMMUNIZATION_COSTS =
      parseCsvToMap("costs/immunizations.csv");
  
  private static final double DEFAULT_PROCEDURE_COST =
      Double.parseDouble(Config.get("generate.costs.default_procedure_cost"));
  private static final double DEFAULT_MEDICATION_COST =
      Double.parseDouble(Config.get("generate.costs.default_medication_cost"));
  private static final double DEFAULT_ENCOUNTER_COST =
      Double.parseDouble(Config.get("generate.costs.default_encounter_cost"));
  private static final double DEFAULT_IMMUNIZATION_COST =
      Double.parseDouble(Config.get("generate.costs.default_immunization_cost"));
  
  private static Map<String, Double> state_adjustment_factors;
  
  private static Map<String, Double> zip_adjustment_factors;
  
  private static Logger costsLogger = LoggerFactory.getLogger(Costs.class);
  private static StringWriter stackWriter = new StringWriter();
  private static PrintWriter stackPrinter = new PrintWriter(stackWriter);
  
  /**
   * Load all cost data needed by the system.
   */
  public static void loadCostData() {  
    try {
      zip_adjustment_factors = parseAdjustmentFactors("costs/zipAdjustmentFactors.csv", "ZIP");
    } catch (Exception e) {
      System.err.println("WARN: unable to load zip code cost data csv: costs/zipAdjustmentFactors.csv - falling back to state");
      e.printStackTrace(stackPrinter);
      costsLogger.warn("unable to load zip code costs - falling back to state data");
      costsLogger.warn(stackWriter.toString());
      zip_adjustment_factors = null;
    }
    try {
      state_adjustment_factors = parseAdjustmentFactors("costs/adjustmentFactors.csv", "STATE");
    } catch (Exception e) {
      System.err.println("ERROR: unable to load state cost data csv: costs/adjustmentFactors.csv");
      e.printStackTrace(stackPrinter);
      costsLogger.error("failed to read state data - no fallback available if everything else failed");
      costsLogger.error(stackWriter.toString());
      state_adjustment_factors = null;
    }
  }
  
  private static Map<String, CostData> parseCsvToMap(String filename) {
    try {
      String rawData = Utilities.readResource(filename);
      List<LinkedHashMap<String, String>> lines = SimpleCSV.parse(rawData);
      
      Map<String, CostData> costMap = new HashMap<>();
      for (Map<String,String> line : lines) {
        String code = line.get("CODE");
        String minStr = line.get("MIN");
        String modeStr = line.get("MODE");
        String maxStr = line.get("MAX");
        
        try {
          double min = Double.parseDouble(minStr);
          double mode = Double.parseDouble(modeStr);
          double max = Double.parseDouble(maxStr);
          costMap.put(code, new CostData(min, mode, max));
        } catch (NumberFormatException nfe) {
          System.err.println(filename + ": Invalid cost for code: '" + code
              + "' -- costs should be numeric but were "
              + "'" + minStr + "', '" + modeStr + "', '" + maxStr + "'");
          System.err.println("Code '" + code + "' will use the default cost");
          nfe.printStackTrace();
        }
      }
      
      return costMap;
    } catch (IOException e) {
      e.printStackTrace();
      throw new ExceptionInInitializerError("Unable to read required file: " + filename);
    }
  }
  
  private static Map<String, Double> parseAdjustmentFactors(String path, String type) {
    try {
      String rawData = Utilities.readResource(path);
      List<LinkedHashMap<String, String>> lines = SimpleCSV.parse(rawData);

      Map<String, Double> costMap = new HashMap<>();
      costsLogger.debug("Scanning lines of " + type.toLowerCase() + " csv file");
      for (Map<String, String> line : lines) {
        String location = line.get(type);
        String factorStr = line.get("ADJ_FACTOR");
        costsLogger.debug("Loading cost data for " + type.toLowerCase() + " " + location);
        try {
          Double factor = Double.valueOf(factorStr);
          costMap.put(location, factor);
          costsLogger.debug("Successfully loaded cost data for " + type.toLowerCase() + " " + location);
        } catch (NumberFormatException nfe) {
          throw new RuntimeException("Invalid cost adjustment factor: " + factorStr, nfe);
        }
      }
      return costMap;
    } catch (IOException e) {
      e.printStackTrace();
      throw new ExceptionInInitializerError(
          "Unable to read file: " + path);
    }
  }

  /**
   * Whether or not this HealthRecord.Entry has an associated cost on a claim.
   * Billing cost is not necessarily reimbursed cost or paid cost.
   * @param entry HealthRecord.Entry
   * @return true if the entry has a cost; false otherwise
   */
  public static boolean hasCost(Entry entry) {
    return (entry instanceof HealthRecord.Procedure)
        || (entry instanceof HealthRecord.Medication)
        || (entry instanceof HealthRecord.Encounter)
        || (entry instanceof HealthRecord.Immunization);
  }

  /**
   * Calculate the cost of this Procedure, Encounter, Medication, etc.
   * 
   * @param entry Entry to calculate cost of.
   * @param patient Person to whom the entry refers to
   * @param provider Provider that performed the service, if any
   * @param payer Entity paying for the service, if any
   * @return Cost, in USD.
   */
  public static double calculateCost(Entry entry, Person patient, Provider provider, String payer) {
    if (!hasCost(entry)) {
      return 0;
    }
    
    String code = entry.codes.get(0).code;
    
    double defaultCost = 0.0;
    Map<String, CostData> costs = null;
    
    if (entry instanceof HealthRecord.Procedure) {
      costs = PROCEDURE_COSTS;
      defaultCost = DEFAULT_PROCEDURE_COST;
    } else if (entry instanceof HealthRecord.Medication) {
      costs = MEDICATION_COSTS;
      defaultCost = DEFAULT_MEDICATION_COST;
    } else if (entry instanceof HealthRecord.Encounter) {
      costs = ENCOUNTER_COSTS;
      defaultCost = DEFAULT_ENCOUNTER_COST;
    } else if (entry instanceof HealthRecord.Immunization) {
      costs = IMMUNIZATION_COSTS;
      defaultCost = DEFAULT_IMMUNIZATION_COST;
    }
    
    double baseCost;
    if (costs != null && costs.containsKey(code)) {
      baseCost = costs.get(code).chooseCost(patient.random);
    } else {
      baseCost = defaultCost;
    }
    
    double locationAdjustment = 1.0;
    
    if (patient != null && patient.attributes.containsKey(Person.STATE)) {
      String state = (String) patient.attributes.get(Person.STATE);
      state = Location.getAbbreviation(state);
      if (state_adjustment_factors.containsKey(state)) {
        locationAdjustment = (double) state_adjustment_factors.get(state);
      } else {
        costsLogger.info("State adjustment for " + state + " not found. Rolling back to default.");
      }
    } else {
      costsLogger.info("Patient is null or does not have a defined state. This shouldn't happen!");
    }
    
    // try to get the zip code adjustment
    
    if (patient != null && patient.attributes.containsKey(Person.ZIP)) {
      String zip = (String) patient.attributes.get(Person.ZIP);
      if (zip_adjustment_factors != null) {
        if (zip_adjustment_factors.containsKey(zip)) {
          locationAdjustment = (double) zip_adjustment_factors.get(zip);
        } else {
          costsLogger.info("Zip code adjustment for " + zip + " (" + patient.attributes.get(Person.CITY) + ") not found. Rolling back to state adjustment or default value.");
        }
      } else {
        costsLogger.info("Zip code adjustment for " + zip + " (" + patient.attributes.get(Person.CITY) + ") not found. Rolling back to state adjustment or default value.");
      }
    } else {
      costsLogger.info("Patient is null or does not have a defined zip code.");
    }
    
    return baseCost * locationAdjustment;
  }
  
  /**
   * Helper class to store a grouping of cost data for a single concept.
   * Currently cost data includes a minimum, maximum, and mode (most common value).
   * Selection of individual prices based on this cost data should be done
   * using the chooseCost(Random) method.
   */
  private static class CostData {
    private double min;
    private double mode;
    private double max;
    
    private CostData(double min, double mode, double max) {
      this.min = min;
      this.mode = mode;
      this.max = max;
    }
    
    /**
     * Select an individual cost based on this cost data. Uses a triangular distribution
     * to pick a randomized value.
     * @param random Source of randomness
     * @return Single cost within the range this set of cost data represents
     */
    private double chooseCost(Random random) {
      return triangularDistribution(min, max, mode, random.nextDouble());
    }
    
    /**
     * Pick a single value based on a triangular distribution. 
     * See: https://en.wikipedia.org/wiki/Triangular_distribution
     * @param min Lower limit of the distribution
     * @param max Upper limit of the distribution
     * @param mode Most common value
     * @param rand A random value between 0-1
     * @return a single value from the distribution
     */
    public static double triangularDistribution(double min, double max, double mode, double rand) {
      double f = (mode - min) / (max - min);
      if (rand < f) {
        return min + Math.sqrt(rand * (max - min) * (mode - min));
      } else {
        return max - Math.sqrt((1 - rand) * (max - min) * (max - mode));
      }
    }
  }
}
