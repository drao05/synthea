package org.mitre.synthea.world.geography.demographics;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCollection;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

/**
 * Class for demographic data based on geographic location.
 * A single instance of Demographics represents a single place. The Ages,
 * Gender, Race, Income, and Education properties are maps of frequency
 * information. This has been recently changed to an abstract class because
 * most exposed methods do not depend on a specific representation of geography
 * but some may.
 * 
 * TODO: add ways to better wrap these maps so they are more accessible and useful.
 * TODO: possibly get rid of Location (or merge with it) - perhaps it is not needed
 */

public abstract class Demographics {

  public long population;
  public Map<String, Double> ages;
  private RandomCollection<String> ageDistribution;
  public Map<String, Double> gender;
  private RandomCollection<String> genderDistribution;
  public Map<String, Double> race;
  private RandomCollection<String> raceDistribution;
  public Map<String, Double> income;
  private RandomCollection<String> incomeDistribution;
  public Map<String, Double> education;
  private RandomCollection<String> educationDistribution;

  public int pickAge(Random random) {
    // lazy-load in case this randomcollection isn't necessary
    if (ageDistribution == null) {
      ageDistribution = buildRandomCollectionFromMap(ages);
    }
    /*
     * Sample Age frequency: "ages": { "0..4": 0.03810425832699584, "5..9": 0.04199539968180355,
     * [truncated] "75..79": 0.04838265689371212, "80..84": 0.037026496153182195, "85..110":
     * 0.040978290790498896 }
     */

    String pickedRange = ageDistribution.next(random);

    String[] range = pickedRange.split("\\.\\.");
    // TODO this seems like it would benefit from better caching
    int low = Integer.parseInt(range[0]);
    int high = Integer.parseInt(range[1]);

    // nextInt is normally exclusive of the top value,
    // so add 1 to make it inclusive
    return random.nextInt((high - low) + 1) + low;
  }

  public String pickGender(Random random) {
    // lazy-load in case this randomcollection isn't necessary
    if (genderDistribution == null) {
      genderDistribution = buildRandomCollectionFromMap(gender);
    }

    /*
     * Sample Gender frequency: "gender": { "male": 0.47638487773697935, "female":
     * 0.5236151222630206 },
     */
    return genderDistribution.next(random);
  }

  public String pickRace(Random random) {
    // lazy-load in case this randomcollection isn't necessary
    if (raceDistribution == null) {
      raceDistribution = buildRandomCollectionFromMap(race);
    }

    /*
     * Sample Race frequency: "race": { "white": 0.932754172245991, "hispanic":
     * 0.028409064399789113, "black": 0.026762094497814148, "asian": 0.014094889727666761, "native":
     * 0.008015564565419232, "other": 0.001 },
     */

    return raceDistribution.next(random);
  }

  public String ethnicityFromRace(String race, Random random) {
    // https://en.wikipedia.org/wiki/Demographics_of_Massachusetts#Race.2C_ethnicity.2C_and_ancestry
    if (race.equals("white")) {
      RandomCollection<String> whiteEthnicities = new RandomCollection<>();
      whiteEthnicities.add(22.8, "irish");
      whiteEthnicities.add(13.9, "italian");
      whiteEthnicities.add(10.7, "english");
      whiteEthnicities.add(7.8, "french");
      whiteEthnicities.add(6.4, "german");
      whiteEthnicities.add(5.0, "polish");
      whiteEthnicities.add(4.7, "portuguese");
      whiteEthnicities.add(4.4, "american");
      whiteEthnicities.add(3.8, "french_canadian");
      whiteEthnicities.add(2.4, "scottish");
      whiteEthnicities.add(1.9, "russian");
      whiteEthnicities.add(1.8, "swedish");
      whiteEthnicities.add(1.2, "greek");
      return whiteEthnicities.next(random);
    } else if (race.equals("hispanic")) {
      RandomCollection<String> hispanicEthnicities = new RandomCollection<>();
      hispanicEthnicities.add(4.1, "puerto_rican");
      hispanicEthnicities.add(1, "mexican");
      hispanicEthnicities.add(1, "central_american");
      hispanicEthnicities.add(1, "south_american");
      return hispanicEthnicities.next(random);
    } else if (race.equals("black")) {
      RandomCollection<String> blackEthnicities = new RandomCollection<>();
      blackEthnicities.add(1.8, "african");
      blackEthnicities.add(1.8, "dominican");
      blackEthnicities.add(1.8, "west_indian");
      return blackEthnicities.next(random);
    } else if (race.equals("asian")) {
      RandomCollection<String> asianEthnicities = new RandomCollection<>();
      asianEthnicities.add(2.0, "chinese");
      asianEthnicities.add(1.1, "asian_indian");
      return asianEthnicities.next(random);
    } else if (race.equals("native")) {
      return "american_indian";
    } else { // race.equals("other")
      return "arab";
    }
  }

  public String languageFromEthnicity(String ethnicity, Random random) {
    // https://apps.mla.org/map_data -> search by State MA
    // or see
    // https://apps.mla.org/map_data_results&SRVY_YEAR=2010&geo=state&state_id=25&county_id=
    // &mode=geographic&lang_id=&zip=&place_id=&cty_id=&region_id=&division_id=&ll=&ea=y&order=
    // &a=y&pc=1
    //
    // these are "manufactured" #s and not based on real citations
    // vietnamese and cambodian removed because our ethnicity/heritage info isn't that granular
    // these numbers are intended to produce the above numbers overall but correlated by ethnicity
    // ex, only people of chinese ethnicity speak chinese
    // these are "manufactured" #s and not based on real citations
    if (ethnicity.equals("irish")) {
      return "english";
    } else if (ethnicity.equals("english")) {
      return "english";
    } else if (ethnicity.equals("american")) {
      return "english";
    } else if (ethnicity.equals("scottish")) {
      return "english";
    } else if (ethnicity.equals("italian")) {
      RandomCollection<String> italianLanguages = new RandomCollection<>();
      italianLanguages.add(95.0, "english");
      italianLanguages.add(5.0, "italian");
      return italianLanguages.next(random);
    } else if (ethnicity.equals("french")) {
      RandomCollection<String> frenchLanguages = new RandomCollection<>();
      frenchLanguages.add(99.0, "english");
      frenchLanguages.add(1.0, "french");
      return frenchLanguages.next(random);
    } else if (ethnicity.equals("french_canadian")) {
      RandomCollection<String> frenchCanadianLanguages = new RandomCollection<>();
      frenchCanadianLanguages.add(99.0, "english");
      frenchCanadianLanguages.add(1.0, "french");
      return frenchCanadianLanguages.next(random);
    } else if (ethnicity.equals("german")) {
      RandomCollection<String> germanLanguages = new RandomCollection<>();
      germanLanguages.add(96.0, "english");
      germanLanguages.add(4.0, "german");
      return germanLanguages.next(random);
    } else if (ethnicity.equals("polish")) {
      return "english";
    } else if (ethnicity.equals("portuguese")) {
      RandomCollection<String> portugueseLanguages = new RandomCollection<>();
      portugueseLanguages.add(37.0, "english");
      portugueseLanguages.add(63.0, "portuguese");
      return portugueseLanguages.next(random);
    } else if (ethnicity.equals("russian")) {
      RandomCollection<String> russianLanguages = new RandomCollection<>();
      russianLanguages.add(62.0, "english");
      russianLanguages.add(38.0, "russian");
      return russianLanguages.next(random);
    } else if (ethnicity.equals("swedish")) {
      return "english";
    } else if (ethnicity.equals("greek")) {
      RandomCollection<String> greekLanguages = new RandomCollection<>();
      greekLanguages.add(66.0, "english");
      greekLanguages.add(34.0, "greek");
      return greekLanguages.next(random);
    } else if (ethnicity.equals("puerto_rican")) {
      RandomCollection<String> puertoRicanLanguages = new RandomCollection<>();
      puertoRicanLanguages.add(30.0, "english");
      puertoRicanLanguages.add(70.0, "spanish");
      return puertoRicanLanguages.next(random);
    } else if (ethnicity.equals("mexican")) {
      RandomCollection<String> mexicanLanguages = new RandomCollection<>();
      mexicanLanguages.add(30.0, "english");
      mexicanLanguages.add(70.0, "spanish");
      return mexicanLanguages.next(random);
    } else if (ethnicity.equals("central_american")) {
      RandomCollection<String> centralAmericanLanguages = new RandomCollection<>();
      centralAmericanLanguages.add(30.0, "english");
      centralAmericanLanguages.add(70.0, "spanish");
      return centralAmericanLanguages.next(random);
    } else if (ethnicity.equals("south_american")) {
      RandomCollection<String> southAmericanLanguages = new RandomCollection<>();
      southAmericanLanguages.add(30.0, "english");
      southAmericanLanguages.add(35.0, "spanish");
      southAmericanLanguages.add(35.0, "portuguese");
      return southAmericanLanguages.next(random);
    } else if (ethnicity.equals("african")) {
      RandomCollection<String> africanLanguages = new RandomCollection<>();
      africanLanguages.add(95.0, "english");
      africanLanguages.add(5.0, "french");
      return africanLanguages.next(random);
    } else if (ethnicity.equals("dominican")) {
      RandomCollection<String> dominicanLanguages = new RandomCollection<>();
      dominicanLanguages.add(30.0, "english");
      dominicanLanguages.add(70.0, "spanish");
      return dominicanLanguages.next(random);
    } else if (ethnicity.equals("west_indian")) {
      RandomCollection<String> westIndianLanguages = new RandomCollection<>();
      westIndianLanguages.add(25.0, "english");
      westIndianLanguages.add(35.0, "spanish");
      westIndianLanguages.add(50.0, "french_creole");
      return westIndianLanguages.next(random);
    } else if (ethnicity.equals("chinese")) {
      RandomCollection<String> chineseLanguages = new RandomCollection<>();
      chineseLanguages.add(25.0, "english");
      chineseLanguages.add(75.0, "chinese");
      return chineseLanguages.next(random);
    } else if (ethnicity.equals("asian_indian")) {
      RandomCollection<String> asianIndianLanguages = new RandomCollection<>();
      asianIndianLanguages.add(75.0, "english");
      asianIndianLanguages.add(25.0, "hindi");
      return asianIndianLanguages.next(random);
    } else if (ethnicity.equals("american_indian")) {
      return "english";
    } else if (ethnicity.equals("arab")) {
      RandomCollection<String> arabLanguages = new RandomCollection<>();
      arabLanguages.add(63.0, "english");
      arabLanguages.add(37.0, "arabic");
      return arabLanguages.next(random);
    } else { // in case of invalid ethnicity
      return "english";
    }
  }

  public int pickIncome(Random random) {
    // lazy-load in case this randomcollection isn't necessary
    if (incomeDistribution == null) {
      Map<String, Double> tempIncome = new HashMap<>(income);
      tempIncome.remove("mean");
      tempIncome.remove("median");
      incomeDistribution = buildRandomCollectionFromMap(tempIncome);
    }

    /*
     * Sample Income frequency: "income": { "mean": 81908, "median": 58933, "00..10":
     * 0.07200000000000001, "10..15": 0.055, "15..25": 0.099, "25..35": 0.079, "35..50": 0.115,
     * "50..75": 0.205, "75..100": 0.115, "100..150": 0.155, "150..200": 0.052000000000000005,
     * "200..999": 0.054000000000000006 },
     */

    String pickedRange = incomeDistribution.next(random);

    String[] range = pickedRange.split("\\.\\.");
    // TODO this seems like it would benefit from better caching
    int low = Integer.parseInt(range[0]) * 1000;
    int high = Integer.parseInt(range[1]) * 1000;

    // nextInt is normally exclusive of the top value,
    // so add 1 to make it inclusive
    return random.nextInt((high - low) + 1) + low;
  }

  public double incomeLevel(int income) {
    // simple linear formula just maps federal poverty level to 0.0 and 75,000 to 1.0
    // 75,000 chosen based on
    // https://www.princeton.edu/~deaton/downloads/
    //   deaton_kahneman_high_income_improves_evaluation_August2010.pdf
    double poverty = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.income.poverty", "11000"));
    double high = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.income.high", "75000"));

    if (income >= high) {
      return 1.0;
    } else if (income <= poverty) {
      return 0.0;
    } else {
      return ((double) income - poverty) / (high - poverty);
    }
  }

  public String pickEducation(Random random) {
    // lazy-load in case this randomcollection isn't necessary
    if (educationDistribution == null) {
      educationDistribution = buildRandomCollectionFromMap(education);
    }

    return educationDistribution.next(random);
  }

  public double educationLevel(String level, Random random) {
    double lessThanHsMin = Double.parseDouble(
        Config.get("generate.demographics.socioeconomic.education.less_than_hs.min", "0.0"));
    double lessThanHsMax = Double.parseDouble(
        Config.get("generate.demographics.socioeconomic.education.less_than_hs.max", "0.5"));
    double hsDegreeMin = Double.parseDouble(
        Config.get("generate.demographics.socioeconomic.education.hs_degree.min", "0.1"));
    double hsDegreeMax = Double.parseDouble(
        Config.get("generate.demographics.socioeconomic.education.hs_degree.max", "0.75"));
    double someCollegeMin = Double.parseDouble(
        Config.get("generate.demographics.socioeconomic.education.some_college.min", "0.3"));
    double someCollegeMax = Double.parseDouble(
        Config.get("generate.demographics.socioeconomic.education.some_college.max", "0.85"));
    double bsDegreeMin = Double.parseDouble(
        Config.get("generate.demographics.socioeconomic.education.bs_degree.min", "0.5"));
    double bsDegreeMax = Double.parseDouble(
        Config.get("generate.demographics.socioeconomic.education.bs_degree.max", "1.0"));

    switch (level) {
      case "less_than_hs":
        return rand(random, lessThanHsMin, lessThanHsMax);
      case "hs_degree":
        return rand(random, hsDegreeMin, hsDegreeMax);
      case "some_college":
        return rand(random, someCollegeMin, someCollegeMax);
      case "bs_degree":
        return rand(random, bsDegreeMin, bsDegreeMax);
      default:
        return 0.0;
    }
  }
  
  private static double rand(Random r, double low, double high) {
    return (low + ((high - low) * r.nextDouble()));
  }

  public double socioeconomicScore(double income, double education, double occupation) {
    double incomeWeight = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.weights.income"));
    double educationWeight = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.weights.education"));
    double occupationWeight = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.weights.occupation"));

    return (income * incomeWeight) + (education * educationWeight)
        + (occupation * occupationWeight);
  }

  public String socioeconomicCategory(double score) {
    double highScore = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.score.high"));
    double middleScore = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.score.middle"));

    if (score >= highScore) {
      return "High";
    } else if (score >= middleScore) {
      return "Middle";
    } else {
      return "Low";
    }
  }
  

  /**
   * Helper function to convert a map of frequencies into a RandomCollection.
   */
  protected static RandomCollection<String> buildRandomCollectionFromMap(Map<String, Double> map) {
    RandomCollection<String> distribution = new RandomCollection<>();
    for (Map.Entry<String, Double> e : map.entrySet()) {
      distribution.add(e.getValue(), e.getKey());
    }
    return distribution;
  }
  
  /**
   * Provide a Person with a map of fields used to define the person's
   * geography.
   * @return Map: string -> object, which defines the location of a Person
   */
  public abstract Map<String, Object> setDemographicsFields();
  
}
