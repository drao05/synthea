package org.mitre.synthea.webservice;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * This service periodically checks for and delete's expired ZIP results files.
 */
@Service
public class CleanupService {

	private final static Logger LOGGER = LoggerFactory.getLogger(CleanupService.class);

	// Max allowed age of ZIP files (used by scheduler task that deletes expired ZIP files)
	@Value("${zip.maxAgeSeconds:86400}")
	private Integer maxZipAgeSeconds;
		
	@Autowired
	private RequestService requestService;
	
    /**
     * Scheduled task for deleting expired ZIP files
     */
    @Scheduled(fixedRateString = "${zip.expiration.testIntervalSeconds:3600}000")
    public void deleteExpiredZipFiles() {
    	try (DirectoryStream<Path> paths = Files.newDirectoryStream(requestService.getZipOutputPath(), "*.zip")) {
    	    paths.forEach(p->checkForExpiredFile(p, maxZipAgeSeconds));
    	} catch(IOException ioex) {
    		LOGGER.error("Error while deleting expired ZIP files", ioex);
    	}
    }
    
    /**
     * Delete file if max age has been exceeded
     */
    private void checkForExpiredFile(Path path, int maxAgeSeconds) {
    	try {
	    	BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
	        Date now = new Date();
	        if (now.getTime() - attributes.creationTime().toMillis() >= maxAgeSeconds * 1000) {
	        	Files.delete(path);
	        	LOGGER.info("Deleted expired file: " + path.getFileName());
	        }
    	} catch(IOException ioex) {
    		LOGGER.error("Error while checking for expired file", ioex);
    	}
    }
}
