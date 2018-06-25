import csv

with open('ACS_15_5YR_S2101_with_ann_synthea.csv', 'rb') as raw, open('demographics_v.csv', 'wb+') as process:
	rawreader = csv.reader(raw, delimiter=',', quotechar='"')
	procwriter = csv.writer(process, delimiter=',', quotechar='"', quoting=csv.QUOTE_MINIMAL)
	procwriter.writerow(['', 'COUNTY', 'NAME', 'STNAME', 'POPESTIMATE2015', 'CTYNAME',
		'TOT_POP', 'TOT_MALE', 'TOT_FEMALE',	'WHITE', 'HISPANIC', 'BLACK', 'ASIAN',
		'NATIVE', 'OTHER', '1', '2', '3', '4', '5', 'LESS_THAN_HS', 
		'HS_DEGREE', 'SOME_COLLEGE', 'BS_DEGREE', '00..11', '11..999'])
	first = True
	next(rawreader)
	next(rawreader)
	for row in rawreader:
		outrow = [None] * 26 # magic number
		outrow[0] = row[0]
		outrow[1] = row[1]
		
		# Process out the "city", "town", "CDP" or whatever suffix
		
		location = row[2].split(',')
		city = ' '.join(location[0].split(' ')[:-1])
		state = location[1].strip()
		outrow[2] = city
		outrow[3] = state
		pop = float(row[3])
		if pop == 0:
			continue
		
		# Gender totals
		malepop = float(row[9])
		femalepop = pop - malepop
		
		outrow[4] = str(pop)
		outrow[5] = '' # TODO: is county really necessary? Use dummy values?
		outrow[6] = '' # TODO: county total pop not necessary?
		outrow[7] = str(malepop/pop)
		outrow[8] = str(femalepop/pop)
		
		# Totals by race or ethnicity
		# TODO: How to handle multiracial people? It's just "other" for now
		whiteonly = float(row[16])
		hisp_latinx = float(row[23])
		blackonly = float(row[17])
		asianonly = float(row[19]) + float(row[20]) # TODO: Group pacific islanders with Asians or other? My hunch is Asians
		nativeonly = float(row[18])
		other = float(row[21]) + float(row[22])
		
		outrow[9] = str(whiteonly/pop)
		outrow[10] = str(hisp_latinx/pop)
		outrow[11] = str(blackonly/pop)
		outrow[12] = str(asianonly/pop)
		outrow[13] = str(nativeonly/pop)
		outrow[14] = str(other/pop)
		
		# Age groups - note they use a different scale than Synthea does by default
		
		outrow[15] = row[11]
		outrow[16] = row[12]
		outrow[17] = row[13]
		outrow[18] = row[14]
		outrow[19] = row[15]
		
		# Education levels
		
		lesshs = float(row[29])
		hseq = float(row[30])
		somecollege = float(row[31])
		bs = float(row[32])
		
		outrow[20] = str(lesshs/pop)
		outrow[21] = str(hseq/pop)
		outrow[22] = str(somecollege/pop)
		outrow[23] = str(bs/pop)
		
		# Income (note that the veteran data only makes a distinction between veterans under and over the poverty line)
		
		belowpoverty = float(row[38])
		poverty_determined = float(row[37])
		
		if poverty_determined == 0:
			outrow[24] = 0.182 # ACS 2012 poverty rate in non-metro areas
			outrow[25] = 1 - 0.182
		else:
			outrow[24] = str(belowpoverty/poverty_determined)
			outrow[25] = str(1 - (belowpoverty/poverty_determined))
		
		procwriter.writerow(outrow)
		
