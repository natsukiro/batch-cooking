import requests
from bs4 import BeautifulSoup
import json

def search_recipes_by_ingredient(ingredient):
    """
    Recherche des recettes sur Marmiton basées sur un ingrédient
    en utilisant les données structurées JSON-LD.
    """
    base_url = "https://www.marmiton.org/recettes/recherche.aspx"
    params = {"aqt": ingredient}

    response = requests.get(base_url, params=params)
    
    if response.status_code == 200:
        soup = BeautifulSoup(response.content, 'html.parser')
        
        # Trouver tous les scripts JSON-LD
        json_ld_scripts = soup.find_all('script', type='application/ld+json')
        
        if not json_ld_scripts:
            print("Aucun script JSON-LD trouvé.")
            return []

        recipes = []
        for script in json_ld_scripts:
            try:
                data = json.loads(script.string)
                
                # Le JSON-LD peut être un dictionnaire unique ou une liste.
                data_list = data if isinstance(data, list) else [data]

                for element in data_list:
                    if isinstance(element, dict) and element.get('@type') == 'ItemList':
                        for item in element.get('itemListElement', []):
                            recipes.append({
                                'title': item.get('name'),
                                'url': item.get('url'),
                                'image': item.get('image')
                            })
                        # Une fois la liste trouvée, on peut sortir de la boucle des scripts
                        if recipes:
                            return recipes
            except json.JSONDecodeError:
                # Ignorer les balises qui ne contiennent pas de JSON valide
                continue
        
        return recipes
    else:
        print(f"Erreur lors de la requête : {response.status_code}")
        return []

def get_ingredients_from_recipe_url(recipe_url):
    """
    Scrape les ingrédients d'une page de recette Marmiton.
    """
    try:
        response = requests.get(recipe_url)
        if response.status_code != 200:
            print(f"Erreur en accédant à {recipe_url}")
            return []

        soup = BeautifulSoup(response.content, 'html.parser')
        
        json_ld_scripts = soup.find_all('script', type='application/ld+json')
        if not json_ld_scripts:
            return []

        for script in json_ld_scripts:
            try:
                data = json.loads(script.string)
                # Le JSON peut être une liste ou un dictionnaire
                data_list = data if isinstance(data, list) else [data]

                for element in data_list:
                    if isinstance(element, dict) and element.get('@type') == 'Recipe':
                        return element.get('recipeIngredient', [])
            except (json.JSONDecodeError, AttributeError):
                continue
        
        return []

    except Exception as e:
        print(f"Une erreur est survenue lors du scraping de {recipe_url}: {e}")
        return []

# Exemple d'utilisation
if __name__ == "__main__":
    # Test de la recherche
    # recipes_found = search_recipes_by_ingredient("courgette")
    # if recipes_found:
    #     print(f"{len(recipes_found)} recettes trouvées pour 'courgette':")
    #     for recipe in recipes_found:
    #         print(f"- {recipe['title']}: {recipe['url']}")
    # else:
    #     print("Aucune recette trouvée.")

    # Test de l'extraction d'ingrédients
    test_url = "https://www.marmiton.org/recettes/recette_gratin-de-courgettes-rapide_17071.aspx"
    print(f"\nExtraction des ingrédients pour : {test_url}")
    ingredients = get_ingredients_from_recipe_url(test_url)
    if ingredients:
        for ing in ingredients:
            print(f"- {ing}")
    else:
        print("Aucun ingrédient trouvé.")