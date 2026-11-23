FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .

# Les données (SQLite) vivent dans un volume.
ENV PR_DB=/data/petitrythme.db
VOLUME ["/data"]
EXPOSE 8080

# Un seul processus, plusieurs fils : SQLite reste simple et sûr.
CMD ["gunicorn", "--bind", "0.0.0.0:8080", "--workers", "1", "--threads", "4", "app:app"]
