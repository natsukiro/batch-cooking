import json
import random
from scraper import search_recipes_by_ingredient, get_ingredients_from_recipe_url
import time

def load_seasonal_ingredients(season="ete"):
    """
    Charge les ingrédients de saison depuis le fichier JSON.
    """
    with open('backend/seasonal_ingredients.json', 'r', encoding='utf-8') as f:
        all_seasons = json.load(f)
    
    ingredients = all_seasons.get(season, {})
    return ingredients.get("legumes", []) + ingredients.get("fruits", [])

def generate_weekly_plan(num_recipes=4):
    """
    Génère un plan de repas pour la semaine.
    """
    # 1. Charger les ingrédients de saison
    seasonal_ingredients = load_seasonal_ingredients()
    if not seasonal_ingredients:
        print("Aucun ingrédient de saison trouvé.")
        return []

    # 2. Choisir quelques ingrédients au hasard pour baser la recherche
    #    Pour s'assurer d'avoir assez de recettes, on en prend plusieurs
    search_ingredients = random.sample(seasonal_ingredients, min(len(seasonal_ingredients), 3))

    # 3. Récupérer des recettes pour ces ingrédients
    all_recipes = []
    for ingredient in search_ingredients:
        print(f"Recherche de recettes pour : {ingredient}...")
        all_recipes.extend(search_recipes_by_ingredient(ingredient))
        # Petite pause pour ne pas surcharger le serveur
        random.shuffle(all_recipes)

    # 4. Sélectionner 4 recettes uniques
    # On utilise un dictionnaire pour s'assurer de l'unicité des URLs
    unique_recipes_dict = {recipe['url']: recipe for recipe in all_recipes}
    unique_recipes = list(unique_recipes_dict.values())
    
    if len(unique_recipes) < num_recipes:
        print(f"Pas assez de recettes trouvées ({len(unique_recipes)}) pour faire un plan de {num_recipes} repas.")
        plan = unique_recipes
    else:
        plan = random.sample(unique_recipes, num_recipes)

    # 5. Extraire les ingrédients pour la liste de courses
    shopping_list = []
    for recipe in plan:
        print(f"Extraction des ingrédients pour : {recipe['title']}...")
        ingredients = get_ingredients_from_recipe_url(recipe['url'])
        shopping_list.extend(ingredients)
        time.sleep(0.5) # Petite pause pour ne pas surcharger le serveur

    # Dédoublonner la liste de courses
    shopping_list = sorted(list(set(shopping_list)))

    return {"plan": plan, "shopping_list": shopping_list}


# Exemple d'utilisation
if __name__ == "__main__":
    result = generate_weekly_plan()
    if result and result['plan']:
        print("\n--- Plan de la semaine ---")
        for recipe in result['plan']:
            print(f"- {recipe['title']}")
        print("------------------------")

        print("\n--- Liste de courses ---")
        for item in result['shopping_list']:
            print(f"- {item}")
        print("------------------------")