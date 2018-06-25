import locale

import csv

adjustments_hrr = {}
adjustments_zip = {}

locale.setlocale(locale.LC_NUMERIC, '')

with open('pa_reimb_hrr_2015.csv', 'rb') as hrrs:
	hrrreader = csv.reader(hrrs, delimiter=',', quotechar='"')
	next(hrrreader)
	for row in hrrreader:
		hrrno = row[0]
		unadjusted = locale.atof(row[4].strip())
		adjusted = locale.atof(row[5].strip())
		adjustments_hrr[hrrno] = adjusted/unadjusted

with open('zip_hsa_hrr_15.csv', 'rb') as crosstable:
	crossreader = csv.reader(crosstable, delimiter=',', quotechar='"')
	next(crossreader)
	for row in crossreader:
		zip = row[0]
		hrrno = row[4]
		adjustments_zip[zip] = adjustments_hrr[hrrno]
	

with open('zipAdjustmentFactors.csv', 'wb+') as zgaf:
	zgaf_writer = csv.writer(zgaf, delimiter=',', quotechar='"', quoting=csv.QUOTE_MINIMAL)
	header = [None] * 2
	header[0] = 'ZIP'
	header[1] = 'ADJ_FACTOR'
	zgaf_writer.writerow(header)
	for k, v in adjustments_zip.iteritems():
		row = [None]*2
		row[0] = k
		row[1] = v
		zgaf_writer.writerow(row)
		
	