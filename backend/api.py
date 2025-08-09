from flask import Flask, jsonify
from flask_cors import CORS
from planner import generate_weekly_plan

app = Flask(__name__)
# Autorise les requêtes depuis une origine spécifique
CORS(app, resources={r"/api/*": {"origins": "http://localhost:8000"}})

@app.route('/api/weekly-plan', methods=['GET'])
def get_weekly_plan():
    """
    Point d'entrée de l'API pour obtenir le plan de la semaine et la liste de courses.
    """
    result = generate_weekly_plan()
    return jsonify(result)

if __name__ == '__main__':
    # Lance le serveur de développement Flask
    # host='0.0.0.0' le rend accessible depuis le réseau local
    app.run(host='0.0.0.0', port=5000, debug=True)