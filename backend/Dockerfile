# Utiliser une image Python officielle comme base
FROM python:3.9-slim

# Définir le répertoire de travail dans le conteneur
WORKDIR /app

# Copier le fichier des dépendances et les installer
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copier le reste du code de l'application
COPY . .

# Exposer le port sur lequel l'application tourne
EXPOSE 5000

# La commande pour lancer l'application
CMD ["python", "api.py"]