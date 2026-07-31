@echo off
echo Starting development environment...

cd docker
docker-compose up -d

echo Waiting for services to start...
timeout /t 30 /nobreak

cd ..
echo Building all services...
call mvn clean install

echo Development environment ready!
echo Infrastructure services running:
echo - Kafka: localhost:9092
echo - Elasticsearch: localhost:9200
echo - PostgreSQL: localhost:5432
echo - Redis: localhost:6379