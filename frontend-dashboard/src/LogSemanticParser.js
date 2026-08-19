/**
 * LogSemanticParser
 * 
 * Simulates an AI intelligence engine by using pattern matching 
 * to extract semantic context, identify root causes, and suggest fixes.
 */

export const parseLogSemanticContext = (log) => {
    const rawMessage = log.message || '';
    const level = log.level || 'INFO';
    const service = log.serviceId || 'unknown';
    
    let note = {
      action: null,
      user: null,
      rootCause: null,
      suggestedFix: null,
      isIgnorable: false,
      extractedMessage: rawMessage,
      hasStackTrace: false,
    };
  
    // 1. Detect Stack Traces
    if (rawMessage.includes('Exception') || rawMessage.includes('at org.springframework') || rawMessage.includes('\tat ')) {
      note.hasStackTrace = true;
      // Extract the first line as the core message
      note.extractedMessage = rawMessage.split('\n')[0];
    }
  
    // 2. Parse INFO logs (e.g. "userid 12345 request for login")
    if (level === 'INFO') {
      const loginMatch = rawMessage.match(/userid\s+(\S+)\s+request for login/i);
      if (loginMatch) {
        note.action = "Login Request";
        note.user = loginMatch[1];
        note.extractedMessage = "User initiated authentication sequence.";
      }
      
      const orderMatch = rawMessage.match(/order\s+(\S+)\s+created/i);
      if (orderMatch) {
        note.action = "Order Created";
        note.extractedMessage = `Order ID ${orderMatch[1]} was successfully processed.`;
      }
    }
  
    // 3. Parse ERROR and WARN logs
    if (level === 'ERROR' || level === 'WARN') {
      
      // Database errors
      if (rawMessage.toLowerCase().includes('database connection refused') || rawMessage.toLowerCase().includes('jdbcconnectionexception')) {
        note.rootCause = "Database unreachable or credentials invalid.";
        note.suggestedFix = "1. Verify PostgreSQL container is running on port 5432.\n2. Check application.yml for correct username/password.\n3. Ensure network bridging allows access.";
      }
      
      // Out of memory
      else if (rawMessage.toLowerCase().includes('outofmemoryerror') || rawMessage.toLowerCase().includes('java heap space')) {
        note.rootCause = "JVM Heap Memory Exhausted.";
        note.suggestedFix = "1. Increase max heap size using -Xmx JVM flag.\n2. Check for memory leaks in recent deployments.\n3. Analyze heap dump using VisualVM.";
      }
  
      // Null Pointers
      else if (rawMessage.toLowerCase().includes('nullpointerexception')) {
        note.rootCause = "Attempted to invoke method on null object reference.";
        note.suggestedFix = "1. Check the stack trace to find the exact line number.\n2. Add null-checks or Optional<> wrappers around the referenced object.";
      }
  
      // Ignorable network warnings
      else if (rawMessage.toLowerCase().includes('timeout') && rawMessage.toLowerCase().includes('firewall')) {
        note.isIgnorable = true;
        note.rootCause = "Routine firewall handshake timeout.";
      }
      
      // Default fallback for unknown errors
      else {
        note.rootCause = "Unhandled Exception Occurred.";
        note.suggestedFix = "1. Review the stack trace below.\n2. Check recent commits for breaking changes.\n3. Reproduce locally to isolate the bug.";
      }
    }
  
    return note;
  };
  
