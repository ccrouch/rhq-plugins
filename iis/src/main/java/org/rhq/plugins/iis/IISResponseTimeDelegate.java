/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.plugins.iis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.domain.measurement.calltime.CallTimeData;
import org.rhq.core.domain.measurement.calltime.CallTimeDataValue;
import org.rhq.core.pluginapi.util.ResponseTimeLogParser;

public class IISResponseTimeDelegate {

    // Default fields
    // time c-ip cs-method cs-uri-stem sc-status time-taken

    private File logDirectory;
    private File previousFile;
    private long previousOffset;
    private ResponseTimeLogParser logParser;

    private Log log = LogFactory.getLog(IISResponseTimeDelegate.class);

    private class IISResponseTimeLogFileFilter implements FileFilter {
        public boolean accept(File f) {
            String fileName = f.getName().toLowerCase();
            return fileName.startsWith("ex") && fileName.endsWith(".log");
        }
    }

    public IISResponseTimeDelegate(String logDirectory) {
        if (logDirectory == null) {
            throw new IllegalArgumentException("logDirectory can not be null");
        }
        this.logDirectory = new File(logDirectory);
    }

    public void parseLogs(CallTimeData data) {
        File lastAccessedFile = getLastAccessedFile();
        if (lastAccessedFile == null) {
            // no files have been written to the logDirectory yet
            log.info("No log files exist yet");
            return;
        }
        log.info("Last accessed file = " + lastAccessedFile);

        if (previousFile == null) {
            // first time we found a file in the logDirectory
            log.info("This is the first time we found a log file");
            previousFile = lastAccessedFile;
            previousOffset = previousFile.length();
            logParser = new IISResponseTimeLogParser(previousFile);
        } else {
            // logs have been rotated
            if (!previousFile.equals(lastAccessedFile)) {
                log.info("Log files have been rotated");
                // so reset the offset to the beginnnig of the file
                previousOffset = previousFile.length();
                logParser = new IISResponseTimeLogParser(previousFile);
            }
        }

        if (logParser == null) {
            log.info("Unexpected error, logParser was null");
            return;
        }

        try {
            logParser.parseLog(data);
        } catch (IOException ioe) {
            log.info("Error parsing log data: " + ioe.getMessage(), ioe);
        }
    }

    private File getLastAccessedFile() {
        File[] logs = this.logDirectory.listFiles(new IISResponseTimeLogFileFilter());
        File lastModifiedFile = null;
        long lastModifiedTime = 0;
        for (File log : logs) {
            if (log.lastModified() > lastModifiedTime) {
                lastModifiedFile = log;
                lastModifiedTime = log.lastModified();
            }
        }
        return lastModifiedFile;
    }

    private class IISResponseTimeLogParser extends ResponseTimeLogParser {

        // IIS records log files in UTC, but we need to report values in local time
        private int tzOffset = TimeZone.getDefault().getRawOffset();
        private DateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        public IISResponseTimeLogParser(File logFile) {
            super(logFile);
        }

        public synchronized void parseLog(CallTimeData callTimeData) throws IOException {
            log.info("Parsing response-time log file " + this.logFile + "...");
            BufferedReader in;
            long newOffset = 0;
            try {
                in = new BufferedReader(new FileReader(this.logFile));

                // record the length now, incase there are more log lines written while
                // we are parsing the ones in the file since the last time we checked it
                newOffset = this.logFile.length();
            } catch (FileNotFoundException e) {
                log.info("Response-time log file '" + this.logFile + "' does not exist.");
                return;
            }

            log.info("Filesize " + newOffset);
            log.info("Skipping " + previousOffset);
            in.skip(IISResponseTimeDelegate.this.previousOffset);
            IISResponseTimeDelegate.this.previousOffset = newOffset;

            String currentLine;
            while ((currentLine = in.readLine()) != null) {
                LogEntry logEntry;
                log.info("Parsing line: " + currentLine);
                try {
                    logEntry = parseLine(currentLine);
                } catch (Exception e) {
                    log.info("Problem parsing line [" + currentLine + "] - cause: " + e);
                    continue;
                }

                String url = logEntry.getUrl();

                // The URL should always begin with a slash. If it doesn't, log an error and skip the entry,
                // so we don't end up with bogus data in the DB.
                if (url.charAt(0) != '/') {
                    String truncatedUrl = url.substring(0, Math.min(url.length(), 120));
                    if (url.length() > 120) {
                        truncatedUrl += "...";
                    }
                    log.info("URL ('" + truncatedUrl
                        + "') parsed from response-time log file does not begin with '/'. " + "Line being parsed is ["
                        + currentLine + "].");
                    continue;
                }

                if (isExcluded(url)) {
                    log.info("URL was excluded");
                    continue;
                }

                // Only collect stats for successful (2xx or 3xx) requests...
                if ((logEntry.getStatusCode() != null)
                    && ((logEntry.getStatusCode() < 200) || (logEntry.getStatusCode() >= 400))) {
                    log.info("Status code was invalid: " + logEntry.getStatusCode());
                    continue;
                }

                String transformedUrl = applyTransforms(url);
                log.info("Original URL: " + url);
                log.info("Transformed: " + transformedUrl);
                callTimeData.addCallData(transformedUrl, new Date(logEntry.getStartTime()), logEntry.getDuration());
            }

            log.info("Results...");
            for (Map.Entry<String, CallTimeDataValue> callTime : callTimeData.getValues().entrySet()) {
                String url = callTime.getKey();
                CallTimeDataValue value = callTime.getValue();
                log.info("Calltime URL: " + url);
                log.info("Calltime Data: " + value);
            }

            in.close();
        }

        // E.g. format
        // 2008-04-18 19:56:18 127.0.0.1 GET /favicon.ico 404 0
        protected LogEntry parseLine(String line) throws Exception {
            LogEntry logEntry;
            try {
                StringTokenizer tokenizer = new StringTokenizer(line);
                String date = tokenizer.nextToken();
                String time = tokenizer.nextToken();
                String ipAddress = tokenizer.nextToken();
                String httpMethod = tokenizer.nextToken();
                String url = tokenizer.nextToken();
                int httpStatus = Integer.parseInt(tokenizer.nextToken());
                long duration = Long.parseLong(tokenizer.nextToken());

                // get the local start time by subtracting the local offset from the UTC parsed time
                long startTime = dateParser.parse(date.trim() + " " + time.trim()).getTime() - tzOffset;

                logEntry = new LogEntry(url, startTime, duration, httpStatus, ipAddress);
            } catch (RuntimeException e) {
                throw new Exception("Failed to parse response time log file line [" + line + "].", e);
            }

            return logEntry;
        }
    }
}
