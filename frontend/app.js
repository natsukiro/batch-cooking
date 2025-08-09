document.addEventListener('DOMContentLoaded', () => {
    const planContainer = document.getElementById('plan');
    const shoppingListContainer = document.getElementById('shopping-list');
    const copyButton = document.getElementById('copy-button');

    // On affiche des messages de chargement
    planContainer.innerHTML = '<p>Génération du plan de la semaine...</p>';
    shoppingListContainer.innerHTML = '<p>Préparation de la liste de courses...</p>';

    fetch('/api/weekly-plan')
        .then(response => {
            if (!response.ok) {
                throw new Error(`Erreur HTTP: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            // Affichage du plan
            planContainer.innerHTML = '';
            if (data.plan && data.plan.length > 0) {
                data.plan.forEach(recipe => {
                    const card = document.createElement('div');
                    card.className = 'recipe-card';

                    const link = document.createElement('a');
                    link.href = recipe.url;
                    link.target = '_blank';

                    const image = document.createElement('img');
                    image.src = recipe.image;
                    image.alt = recipe.title;

                    const title = document.createElement('h3');
                    title.textContent = recipe.title;

                    link.appendChild(image);
                    link.appendChild(title);
                    card.appendChild(link);
                    planContainer.appendChild(card);
                });
            } else {
                planContainer.innerHTML = '<p>Impossible de générer un plan.</p>';
            }

            // Affichage de la liste de courses
            shoppingListContainer.innerHTML = '';
            if (data.shopping_list && data.shopping_list.length > 0) {
                const ul = document.createElement('ul');
                data.shopping_list.forEach(item => {
                    const li = document.createElement('li');
                    li.textContent = item;
                    ul.appendChild(li);
                });
                shoppingListContainer.appendChild(ul);
                copyButton.style.display = 'block'; // Affiche le bouton
            } else {
                shoppingListContainer.innerHTML = '<p>Aucun ingrédient à afficher.</p>';
            }
        })
        .catch(error => {
            console.error('Erreur lors de la récupération du plan:', error);
            planContainer.innerHTML = `<p>Une erreur est survenue. Vérifiez que le serveur backend est bien lancé.</p>`;
            shoppingListContainer.innerHTML = '';
        });

    // Gestion du bouton "copier"
    copyButton.addEventListener('click', () => {
        const listItems = shoppingListContainer.querySelectorAll('li');
        const textToCopy = Array.from(listItems).map(li => li.textContent).join('\n');

        if (navigator.clipboard) {
            navigator.clipboard.writeText(textToCopy).then(() => {
                copyButton.textContent = 'Copié !';
                setTimeout(() => {
                    copyButton.textContent = 'Copier la liste';
                }, 2000);
            }).catch(err => {
                console.error('Impossible de copier la liste :', err);
                alert('La copie a échoué.');
            });
        }
    });
});