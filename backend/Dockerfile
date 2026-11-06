FROM python:3.12-slim
WORKDIR /app
COPY server.py .
ENV RADAR_HOST=0.0.0.0 RADAR_DB=/data/radar.db
VOLUME /data
EXPOSE 8080
CMD ["python", "server.py"]
