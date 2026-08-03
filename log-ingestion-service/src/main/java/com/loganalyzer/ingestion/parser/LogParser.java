package com.loganalyzer.ingestion.parser;

import com.loganalyzer.ingestion.model.LogEntry;
import com.loganalyzer.ingestion.model.LogRaw;

public interface LogParser {
    LogEntry parse(LogRaw rawLog);
    boolean supports(LogRaw rawLog);
}
