"""Initial navigation translations. Drafts require a native-speaker release review."""
from pathlib import Path
from xml.sax.saxutils import escape
ROOT=Path(__file__).resolve().parents[1]
data={
 'en-rGB':['My kitchen','Recipe book','Meal planner','Shopping list','Pantry','Settings','Save','Cancel','Delete','Add recipe','Start cooking','Next step','Previous step'],
 'en-rUS':['My kitchen','Recipe book','Meal planner','Shopping list','Pantry','Settings','Save','Cancel','Delete','Add recipe','Start cooking','Next step','Previous step'],
 'es':['Mi cocina','Recetario','Plan de comidas','Lista de la compra','Despensa','Ajustes','Guardar','Cancelar','Eliminar','Añadir receta','Empezar a cocinar','Paso siguiente','Paso anterior'],
 'fr':['Ma cuisine','Livre de recettes','Menus de la semaine','Liste de courses','Garde-manger','Paramètres','Enregistrer','Annuler','Supprimer','Ajouter une recette','Commencer à cuisiner','Étape suivante','Étape précédente'],
 'de':['Meine Küche','Rezeptbuch','Essensplaner','Einkaufsliste','Vorratskammer','Einstellungen','Speichern','Abbrechen','Löschen','Rezept hinzufügen','Kochen starten','Nächster Schritt','Vorheriger Schritt'],
 'it':['La mia cucina','Ricettario','Piano dei pasti','Lista della spesa','Dispensa','Impostazioni','Salva','Annulla','Elimina','Aggiungi ricetta','Inizia a cucinare','Passaggio successivo','Passaggio precedente'],
 'pt-rBR':['Minha cozinha','Livro de receitas','Planejador de refeições','Lista de compras','Despensa','Configurações','Salvar','Cancelar','Excluir','Adicionar receita','Começar a cozinhar','Próximo passo','Passo anterior'],
 'pt-rPT':['A minha cozinha','Livro de receitas','Plano de refeições','Lista de compras','Despensa','Definições','Guardar','Cancelar','Eliminar','Adicionar receita','Começar a cozinhar','Próximo passo','Passo anterior'],
 'nl':['Mijn keuken','Receptenboek','Maaltijdplanner','Boodschappenlijst','Voorraadkast','Instellingen','Opslaan','Annuleren','Verwijderen','Recept toevoegen','Begin met koken','Volgende stap','Vorige stap'],
 'pl':['Moja kuchnia','Książka kucharska','Plan posiłków','Lista zakupów','Spiżarnia','Ustawienia','Zapisz','Anuluj','Usuń','Dodaj przepis','Zacznij gotować','Następny krok','Poprzedni krok'],
 'ar':['مطبخي','كتاب الوصفات','مخطط الوجبات','قائمة التسوق','المؤن','الإعدادات','حفظ','إلغاء','حذف','إضافة وصفة','ابدأ الطهي','الخطوة التالية','الخطوة السابقة'],
 'hi':['मेरी रसोई','रेसिपी बुक','भोजन योजनाकार','खरीदारी सूची','भंडार','सेटिंग','सहेजें','रद्द करें','हटाएँ','रेसिपी जोड़ें','खाना बनाना शुरू करें','अगला चरण','पिछला चरण']
}
keys=['nav_home','nav_recipes','nav_plan','nav_shop','nav_pantry','nav_settings','save','cancel','delete','add_recipe','cook','next','previous']
for locale,values in data.items():
    directory=ROOT/f'app/src/main/res/values-{locale}';directory.mkdir(parents=True,exist_ok=True)
    if (directory/'strings.xml').exists():continue  # Never replace completed translations with the bootstrap subset.
    def android_escape(s):return escape(s).replace("'","\\'")
    (directory/'strings.xml').write_text('<resources>\n'+''.join(f'  <string name="{key}">{android_escape(value)}</string>\n' for key,value in zip(keys,values))+'</resources>\n',encoding='utf-8')
print('Initial navigation resources created only where absent; existing translations preserved.')
