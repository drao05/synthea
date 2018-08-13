package org.mitre.synthea.world.geography.location;

import com.google.common.collect.Table;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.geography.StateAbbreviationsLoader;
import org.mitre.synthea.world.geography.demographics.ACSFactFinderDemographicsLoader;
import org.mitre.synthea.world.geography.demographics.CityStateDemographics;
import org.mitre.synthea.world.geography.demographics.Demographics;
import org.mitre.synthea.world.geography.demographics.DefaultDemographicsLoader;
import org.mitre.synthea.world.geography.demographics.DemographicsLoader;
import org.mitre.synthea.world.geography.place.Place;
import org.mitre.synthea.world.geography.place.PlaceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Location is effectively a shim used to enable runtime selection between
 * either a single place or a group of places in a state. It is tied to the
 * city/state geographical hierarchy.
 * NOTE: Demographics has todos to merge the class with Location. Perhaps we
 * or they might want to do this if we migrate to using VSAs/HSAs/HRRs.
 */

public class Location {
  private static StateAbbreviationsLoader abbrLoader = new StateAbbreviationsLoader(Config.get("generate.geography.zipcodes.default_file"), null);
  private static PlaceLoader placeLoader = new PlaceLoader(
      Config.get("generate.geography.place.headers.state_header", "USPS"),
      Config.get("generate.geography.place.headers.abbr_header", "ST"),
      Config.get("generate.geography.place.headers.name_header", "NAME"),
      Config.get("generate.geography.place.headers.postal_code_header", "ZCTA5"), 
      Config.get("generate.geography.place.headers.lat_header", "LAT"),
      Config.get("generate.geography.place.headers.lon_header", "LON"));
  private static Logger locationLogger = LoggerFactory.getLogger(Location.class);
  private static StringWriter stackWriter = new StringWriter();
  private static PrintWriter stackPrinter = new PrintWriter(stackWriter);
  private static Map<String, String> stateAbbreviations = abbrLoader.loadAbbreviations();
  private static Map<String, String> timezones = loadTimezones();
  private static DemographicsLoader demographicsLoader;
  private static String demographicsFile;
  
  static {
    demographicsFile = Config.get("generate.demographics.default_file");
    String format = Config.get("generate.demographics.default_file_format");
    if (format.equals("acs_factfinder")) {
      demographicsLoader = new ACSFactFinderDemographicsLoader();
    } else {
      demographicsLoader = new DefaultDemographicsLoader();
    }
  }

  private long totalPopulation;

  // cache the population by city name for performance
  private Map<String, Long> populationByCity;
  private Map<String, List<Place>> zipCodes;

  private String city;
  private Map<String, CityStateDemographics> demographics;

  /**
   * Location is a set of demographic and place information.
   * @param state The full name of the state.
   *     e.g. "Ohio" and not an abbreviation.
   * @param city The full name of the city.
   *     e.g. "Columbus" or null for an entire state.
   */
  public Location(String state, String city) {
    locationLogger.debug("Attempting to create CityStateLocation(" + city + ", " + state + ")");
    try {
      this.city = city;
      Table<String,String,CityStateDemographics> allDemographics = demographicsLoader.load(state, demographicsFile);
      
      // this still works even if only 1 city given,
      // because allDemographics will only contain that 1 city
      this.demographics = allDemographics.row(state);

      if (city != null && !demographics.containsKey(city)) {
        throw new Exception("The city " + city + " was not found in the demographics file.");
      }

      long runningPopulation = 0;
      populationByCity = new LinkedHashMap<>(); // linked to ensure consistent iteration order
      for (CityStateDemographics d : this.demographics.values()) {
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
        Place place = (Place) placeLoader.placeFromRow(line);
        
        if (!place.sameState(state)) {
          continue;
        }
        
        if (!zipCodes.containsKey(place.name)) {
          zipCodes.put(place.name, new ArrayList<Place>());
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
    List<Place> zipsForCity = zipCodes.get(cityName);
    
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
    List<Place> zipsForCity = null;

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
    
    Place place = null;
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
   * Get the index for a state. This maybe useful for
   * exporters where you want to generate a list of unique
   * identifiers that do not collide across state-boundaries.
   * @param state State name. e.g. "Massachusetts"
   * @return state index. e.g. 1 or 50
   */
  public static int getIndex(String state) {
    int index = 0;
    for (String stateName : stateAbbreviations.keySet()) {
      if (stateName.equals(state)) {
        return index;
      }
      index++;
    }
    return index;
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

  private static Map<String, String> loadTimezones() {
    HashMap<String, String> timezones = new HashMap<String, String>();
    String filename = null;
    try {
      filename = Config.get("generate.geography.timezones.default_file");
      String csv = Utilities.readResource(filename);
      List<? extends Map<String,String>> tzlist = SimpleCSV.parse(csv);

      for (Map<String,String> line : tzlist) {
        String state = line.get("STATE");
        String timezone = line.get("TIMEZONE");
        timezones.put(state, timezone);
      }
    } catch (Exception e) {
      System.err.println("ERROR: unable to load timezones csv: " + filename);
      e.printStackTrace();
    }
    return timezones;
  }

  /**
   * Get the full name of the timezone by the full name of the state.
   * Timezones are approximate.
   * @param state The full name of the state (e.g. "Massachusetts")
   * @return The full name of the timezone (e.g. "Eastern Standard Time")
   */
  public static String getTimezoneByState(String state) {
    return timezones.get(state);
  }
}
