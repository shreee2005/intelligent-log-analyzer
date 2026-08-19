const winston = require('winston');
const axios = require('axios');

class IntelligentLogTransport extends winston.Transport {
  constructor(opts) {
    super(opts);
    this.serviceId = opts.serviceId || 'node-service';
  }

  log(info, callback) {
    setImmediate(() => {
      this.emit('logged', info);
    });

    axios.post('http://localhost:8086/api/v1/logs', {
      serviceId: this.serviceId,
      level: info.level.toUpperCase(),
      format: 'JSON',
      message: info.message,
      timestamp: new Date().toISOString()
    }).catch(err => console.error("Log forwarding failed. Is port 8086 running?"));

    callback();
  }
}

// 1. Configure the Logger with our custom Appender
const logger = winston.createLogger({
  level: 'info',
  transports: [
    new winston.transports.Console(),
    new IntelligentLogTransport({ serviceId: 'my-demo-app' })
  ]
});

console.log("Starting Real Application Test...");

// 2. The developer writes normal code, oblivious to the API Gateway!
setTimeout(() => {
    logger.info("User 'Alex' successfully logged in from IP 192.168.1.5");
}, 1000);

setTimeout(() => {
    logger.info("Alex added 'Wireless Headphones' to cart.");
}, 2000);

setTimeout(() => {
    logger.warn("High latency detected on Payment API (850ms)");
}, 3000);

setTimeout(() => {
    logger.error("Database connection refused during payment processing.");
}, 4000);

setTimeout(() => {
    console.log("Finished sending logs! Check your React Dashboard!");
}, 5000);
