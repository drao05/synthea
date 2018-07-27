package org.mitre.synthea.world.geography;

import com.google.common.collect.Table;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.sis.geometry.DirectPosition2D;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Location {
  private static AbbreviationsLoader abbrLoader = new StateAbbreviationsLoader(Config.get("generate.geography.zipcodes.default_file"), null);
  private static StateCityZipPlaceFactory placeLoader = new StateCityZipPlaceFactory("USPS", "ST", "NAME", "ZCTA5", "LAT", "LON");
  private static Map<String, String> stateAbbreviations = abbrLoader.loadAbbreviations();
  private static Logger locationLogger = LoggerFactory.getLogger(Location.class);
  private static StringWriter stackWriter = new StringWriter();
  private static PrintWriter stackPrinter = new PrintWriter(stackWriter);

  private long totalPopulation;

  // cache the population by city name for performance
  private Map<String, Long> populationByCity;
  private Map<String, List<StateCityZipPlace>> zipCodes;

  private String city;
  private Map<String, Demographics> demographics;

  /**
   * Location is a set of demographic and place information.
   * @param state The full name of the state.
   *     e.g. "Ohio" and not an abbreviation.
   * @param city The full name of the city.
   *     e.g. "Columbus" or null for an entire state.
   */
  public Location(String state, String city) {
    locationLogger.debug("Attempting to create Location(" + city + ", " + state + ")");
    try {
      this.city = city;
      Table<String,String,Demographics> allDemographics = Demographics.load(state);
      
      // this still works even if only 1 city given,
      // because allDemographics will only contain that 1 city
      this.demographics = allDemographics.row(state);

      if (city != null && !demographics.containsKey(city)) {
        throw new Exception("The city " + city + " was not found in the demographics file.");
      }

      long runningPopulation = 0;
      populationByCity = new LinkedHashMap<>(); // linked to ensure consistent iteration order
      for (Demographics d : this.demographics.values()) {
        long pop = d.population;
        runningPopulation += pop;
        populationByCity.put(d.city, pop);
      }
      
      totalPopulation = runningPopulation;
      
    } catch (Exception e) {
      System.err.println("ERROR: unable to load demographics for " + city + ", " + state);
      e.printStackTrace(stackPrinter);
      locationLogger.error(stackWriter.toString());
      throw new ExceptionInInitializerError(e);
    }

    String filename = null;
    try {
      filename = Config.get("generate.geography.zipcodes.default_file");
      String csv = Utilities.readResource(filename);
      List<? extends Map<String,String>> ziplist = SimpleCSV.parse(csv);

      zipCodes = new HashMap<>();
      for (Map<String,String> line : ziplist) {
        StateCityZipPlace place = (StateCityZipPlace) placeLoader.placeFromRow(line);
        
        if (!place.sameState(state)) {
          continue;
        }
        
        if (!zipCodes.containsKey(place.name)) {
          zipCodes.put(place.name, new ArrayList<StateCityZipPlace>());
        }
        zipCodes.get(place.name).add(place);
      }
    } catch (Exception e) {
      System.err.println("ERROR: unable to load zips csv: " + filename);
      e.printStackTrace(stackPrinter);
      locationLogger.error(stackWriter.toString());
      throw new ExceptionInInitializerError(e);
    }
  }
  
  
  /**
   * Get the zip code for the given city name. 
   * If a city has more than one zip code, this picks a random one.
   * 
   * @param cityName Name of the city
   * @return a zip code for the given city
   */
  public String getZipCode(String cityName) {
    List<StateCityZipPlace> zipsForCity = zipCodes.get(cityName);
    
    if (zipsForCity == null) {
      zipsForCity = zipCodes.get(cityName + " Town");
    }
    
    if (zipsForCity == null || zipsForCity.isEmpty()) {
      return "00000"; // if we don't have the city, just use a dummy
    } else if (zipsForCity.size() >= 1) {
      return zipsForCity.get(0).postalCode;
    }
    return "00000";
  }

  public long getPopulation(String cityName) {
    return populationByCity.getOrDefault(cityName, 0L);
  }

  /**
   * Pick the name of a random city from the current "world".
   * If only one city was selected, this will return that one city.
   * 
   * @param random Source of randomness
   * @return Demographics of a random city.
   */
  public Demographics randomCity(Random random) {
    if (city != null) {
      // if we're only generating one city at a time, just use that one city
      return demographics.get(city);
    }
    return demographics.get(randomCityName(random));
  }
  
  /**
   * Pick a random city name, weighted by population.
   * @param random Source of randomness
   * @return a city name
   */
  public String randomCityName(Random random) {
    long targetPop = (long) (random.nextDouble() * totalPopulation);

    for (Map.Entry<String, Long> city : populationByCity.entrySet()) {
      targetPop -= city.getValue();

      if (targetPop < 0) {
        return city.getKey();
      }
    }

    // should never happen
    throw new RuntimeException("Unable to select a random city name.");
  }

  /**
   * Assign a geographic location to the given Person. Location includes City, State, Zip, and
   * Coordinate. If cityName is given, then Zip and Coordinate are restricted to valid values for
   * that city. If cityName is not given, then picks a random city from the list of all cities.
   * 
   * @param person
   *          Person to assign location information
   * @param cityName
   *          Name of the city, or null to choose one randomly
   */
  public void assignPoint(Person person, String cityName) {
    List<StateCityZipPlace> zipsForCity = null;

    if (cityName == null) {
      int size = zipCodes.keySet().size();
      cityName = (String) zipCodes.keySet().toArray()[person.randInt(size)];
    }
    zipsForCity = zipCodes.get(cityName);

    if (zipsForCity == null) {
      zipsForCity = zipCodes.get(cityName + " Town");
    }
    
    if (zipsForCity == null) {
      zipsForCity = zipCodes.get(cityName + " Center");
    }
    
    if (zipsForCity == null) {
      zipsForCity = zipCodes.get(cityName + " City");
    }
    
    if (zipsForCity == null) {
      zipsForCity = zipCodes.get(cityName + " corporation");
    }
    
    if (zipsForCity == null) {
      if (cityName.contains(" ")) {
        zipsForCity = zipCodes.get(cityName.substring(0, cityName.lastIndexOf(" ")));
      }
    }
    
    if (zipsForCity == null) {
      throw new RuntimeException("ERROR: No zips for " + cityName + " found. Please check to make sure that"
          + " the city is somehow represented in both the demographics and zipcodes file.");
    }
    
    StateCityZipPlace place = null;
    if (zipsForCity.size() == 1) {
      place = zipsForCity.get(0);
    } else {
      // pick a random one
      place = zipsForCity.get(person.randInt(zipsForCity.size()));
    }
    
    if (place != null) {
      person.attributes.put(Person.COORDINATE, place.getLatLon());
    }
  }

  /**
   * Get the abbreviation for a state.
   * @param state State name. e.g. "Massachusetts"
   * @return state abbreviation. e.g. "MA"
   */
  public static String getAbbreviation(String state) {
    return stateAbbreviations.get(state);
  }
  
  /**
   * Get the state name from an abbreviation.
   * @param abbreviation State abbreviation. e.g. "MA"
   * @return state name. e.g. "Massachusetts"
   */
  public static String getStateName(String abbreviation) {
    for (String name : stateAbbreviations.keySet()) {
      if (stateAbbreviations.get(name).equalsIgnoreCase(abbreviation)) {
        return name;
      }
    }
    return null;
  }
}
