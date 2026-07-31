@echo off
echo Stopping development environment...

cd docker
docker-compose down

echo Development environment stopped.