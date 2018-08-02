package org.mitre.synthea.world.geography.demographics;

import java.util.Random;

public interface Demographics {

  int pickAge(Random random);

  String pickGender(Random random);

  String pickRace(Random random);

  String ethnicityFromRace(String race, Random random);

  String languageFromEthnicity(String ethnicity, Random random);

  int pickIncome(Random random);

  double incomeLevel(int income);

  String pickEducation(Random random);

  double educationLevel(String level, Random random);

  double socioeconomicScore(double income, double education, double occupation);

  String socioeconomicCategory(double score);

}