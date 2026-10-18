"""Reproducible translated screenshot copy and isolated sample data; no store contact.
These translations require native-language approval before release.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
data = {}

def add(locale, titles, subtitles, footer, sample):
    data[locale] = {
        'native_language_approved': False,
        'headlines': titles.split('|'), 'subheadlines': subtitles.split('|'),
        'footer': footer,
        'sample': dict(zip(['title', 'description', 'ingredients', 'steps', 'pastaTitle',
                            'pastaIngredients', 'pastaSteps', 'collection', 'easy', 'category'], sample)),
    }

add('en-GB',
    'Your personal recipe book|Plan a week of meals|Shopping from your plans|Cook step by step|Review recipe imports|Keep track of your pantry|Recipes saved on your tablet|Make room for good food',
    'Recipes, photos and notes, together.|Adjust portions and organise your week.|Combine ingredients and deduct pantry quantities.|Touch controls, narration and multiple timers.|Premium import; photo text recognition is English only.|Quantities, check dates and optional reminders.|Read saved recipes without an internet connection.|Reusable templates and extra plans require Premium.',
    'Development preview · Sample recipes · Purchases disabled · Voice availability varies',
    ['Lemon and herb chicken', 'Golden chicken with vegetables and a squeeze of lemon.',
     ['300 g chicken','200 g chickpeas','150 g tomatoes','1 lemon','15 ml olive oil','1 courgette'],
     ['Heat the oven to 200°C. Slice the courgette and tomatoes.',
      'Place the vegetables, chickpeas and chicken in a roasting dish. Add oil and lemon.',
      'Roast until the chicken is fully cooked. Check the thickest part with a food thermometer.'],
     'Tomato pasta', ['400 g pasta','600 g tomatoes','2 cloves garlic','30 ml olive oil'],
     ['Simmer the tomatoes and garlic in olive oil until soft.', 'Cook the pasta according to the packet instructions, drain and mix with the sauce.'],
     'Everyday favourites', 'Easy', 'Ingredients'])
data['en-US'] = json.loads(json.dumps(data['en-GB']))
data['en-US']['subheadlines'][1] = 'Adjust portions and organize your week.'
data['en-US']['sample']['collection'] = 'Everyday favorites'
data['en-US']['sample']['ingredients'][-1] = '1 zucchini'
data['en-US']['sample']['steps'][0] = 'Heat the oven to 200°C (392°F). Slice the zucchini and tomatoes.'

add('de-DE',
    'Dein persönliches Kochbuch|Mahlzeiten für die Woche|Einkaufen nach Plan|Schritt für Schritt kochen|Rezeptimporte prüfen|Vorräte im Blick|Rezepte auf deinem Tablet|Mehr Zeit für gutes Essen',
    'Rezepte, Fotos und Notizen an einem Ort.|Portionen anpassen und die Woche organisieren.|Zutaten bündeln und Vorratsmengen abziehen.|Touch-Bedienung, Sprachausgabe und mehrere Timer.|Import mit Premium; Foto-Texterkennung nur auf Englisch.|Mengen, Prüftermine und optionale Erinnerungen.|Gespeicherte Rezepte ohne Internet lesen.|Vorlagen und zusätzliche Pläne benötigen Premium.',
    'Entwicklungsvorschau · Beispielrezepte · Käufe deaktiviert · Sprachverfügbarkeit variiert',
    ['Zitronen-Kräuter-Hähnchen', 'Goldbraunes Hähnchen mit Gemüse und einem Spritzer Zitrone.',
     ['300 g Hähnchen','200 g Kichererbsen','150 g Tomaten','1 Zitrone','15 ml Olivenöl','1 Zucchini'],
     ['Den Ofen auf 200°C vorheizen. Zucchini und Tomaten schneiden.',
      'Gemüse, Kichererbsen und Hähnchen in eine Ofenform geben. Öl und Zitrone hinzufügen.',
      'Braten, bis das Hähnchen vollständig durchgegart ist. Die dickste Stelle mit einem Lebensmittelthermometer prüfen.'],
     'Pasta mit Tomaten', ['400 g Pasta','600 g Tomaten','2 Zehen Knoblauch','30 ml Olivenöl'],
     ['Tomaten und Knoblauch in Olivenöl weich köcheln lassen.', 'Pasta nach Packungsanweisung kochen, abgießen und mit der Sauce mischen.'],
     'Lieblingsgerichte', 'Einfach', 'Zutaten'])

add('es-ES',
    'Tu recetario personal|Planifica el menú semanal|Compra según tu menú|Cocina paso a paso|Revisa las importaciones|Tu despensa al día|Recetas en tu tableta|Más tiempo para cocinar',
    'Recetas, fotos y notas en un solo lugar.|Ajusta las raciones y organiza tu semana.|Agrupa ingredientes y descuenta existencias.|Controles táctiles, narración y temporizadores.|Importación Premium; reconocimiento de fotos solo en inglés.|Cantidades, fechas de revisión y avisos opcionales.|Lee las recetas guardadas sin conexión.|Las plantillas y los planes extra requieren Premium.',
    'Versión de desarrollo · Recetas de ejemplo · Compras desactivadas · La voz puede variar',
    ['Pollo al limón con hierbas', 'Pollo dorado con verduras y un toque de limón.',
     ['300 g pollo','200 g garbanzos','150 g tomates','1 limón','15 ml aceite de oliva','1 calabacín'],
     ['Precalienta el horno a 200°C. Corta el calabacín y los tomates.',
      'Pon las verduras, los garbanzos y el pollo en una fuente. Añade aceite y limón.',
      'Asa hasta que el pollo esté completamente cocinado. Comprueba la parte más gruesa con un termómetro de cocina.'],
     'Pasta con tomate', ['400 g pasta','600 g tomates','2 dientes de ajo','30 ml aceite de oliva'],
     ['Cocina los tomates y el ajo a fuego lento con aceite de oliva hasta que estén tiernos.', 'Cuece la pasta según el envase, escúrrela y mézclala con la salsa.'],
     'Favoritos de cada día', 'Fácil', 'Ingredientes'])

add('fr-FR',
    'Votre carnet de recettes|Les repas de la semaine|Des courses bien préparées|Cuisinez étape par étape|Vérifiez les imports|Gardez vos provisions en vue|Vos recettes sur tablette|Du temps pour bien manger',
    'Recettes, photos et notes au même endroit.|Ajustez les portions et organisez la semaine.|Regroupez les ingrédients et déduisez vos provisions.|Commandes tactiles, narration et minuteurs.|Import Premium ; reconnaissance des photos en anglais uniquement.|Quantités, dates de vérification et rappels facultatifs.|Lisez vos recettes enregistrées hors connexion.|Les modèles et plans supplémentaires nécessitent Premium.',
    'Aperçu de développement · Recettes exemples · Achats désactivés · Voix selon disponibilité',
    ['Poulet au citron et aux herbes', 'Du poulet doré, des légumes et une touche de citron.',
     ['300 g poulet','200 g pois chiches','150 g tomates','1 citron','15 ml huile d’olive','1 courgette'],
     ['Préchauffez le four à 200°C. Coupez la courgette et les tomates.',
      'Placez les légumes, les pois chiches et le poulet dans un plat. Ajoutez l’huile et le citron.',
      'Faites rôtir jusqu’à cuisson complète du poulet. Vérifiez la partie la plus épaisse avec un thermomètre alimentaire.'],
     'Pâtes à la tomate', ['400 g pâtes','600 g tomates','2 gousses d’ail','30 ml huile d’olive'],
     ['Faites mijoter les tomates et l’ail dans l’huile d’olive jusqu’à ce qu’ils soient tendres.', 'Cuisez les pâtes selon le paquet, égouttez-les et mélangez-les à la sauce.'],
     'Favoris du quotidien', 'Facile', 'Ingrédients'])

add('it-IT',
    'Il tuo ricettario personale|Pianifica la settimana|La spesa dai tuoi menu|Cucina passo dopo passo|Controlla le importazioni|La dispensa sotto controllo|Ricette sul tuo tablet|Più spazio alla buona cucina',
    'Ricette, foto e note in un solo posto.|Regola le porzioni e organizza la settimana.|Raggruppa gli ingredienti e detrai le scorte.|Comandi touch, narrazione e più timer.|Importazione Premium; foto riconosciute solo in inglese.|Quantità, date di controllo e promemoria facoltativi.|Leggi le ricette salvate senza connessione.|Modelli e piani aggiuntivi richiedono Premium.',
    'Anteprima di sviluppo · Ricette di esempio · Acquisti disattivati · Voce secondo disponibilità',
    ['Pollo al limone e alle erbe', 'Pollo dorato con verdure e un tocco di limone.',
     ['300 g pollo','200 g ceci','150 g pomodori','1 limone','15 ml olio di oliva','1 zucchina'],
     ['Scalda il forno a 200°C. Taglia la zucchina e i pomodori.',
      'Metti verdure, ceci e pollo in una teglia. Aggiungi olio e limone.',
      'Arrostisci fino a completa cottura del pollo. Controlla la parte più spessa con un termometro alimentare.'],
     'Pasta al pomodoro', ['400 g pasta','600 g pomodori','2 spicchi aglio','30 ml olio di oliva'],
     ['Fai sobbollire pomodori e aglio nell’olio fino a renderli morbidi.', 'Cuoci la pasta secondo la confezione, scolala e mescolala al sugo.'],
     'Preferiti di ogni giorno', 'Facile', 'Ingredienti'])

add('nl-NL',
    'Je persoonlijke receptenboek|Plan je weekmenu|Boodschappen vanuit je plan|Kook stap voor stap|Controleer receptimports|Houd je voorraad bij|Recepten op je tablet|Ruimte voor lekker eten',
    'Recepten, foto’s en notities bij elkaar.|Pas porties aan en organiseer je week.|Voeg ingrediënten samen en trek voorraad af.|Aanraakbediening, gesproken stappen en timers.|Premium-import; fotoherkenning alleen voor Engelse tekst.|Hoeveelheden, controledatums en optionele herinneringen.|Lees opgeslagen recepten zonder internet.|Sjablonen en extra plannen vereisen Premium.',
    'Ontwikkelversie · Voorbeeldrecepten · Aankopen uitgeschakeld · Spraakbeschikbaarheid varieert',
    ['Citroenkip met kruiden', 'Goudbruine kip met groenten en een vleugje citroen.',
     ['300 g kip','200 g kikkererwten','150 g tomaten','1 citroen','15 ml olijfolie','1 courgette'],
     ['Verwarm de oven tot 200°C. Snijd de courgette en tomaten.',
      'Leg groenten, kikkererwten en kip in een ovenschaal. Voeg olie en citroen toe.',
      'Rooster tot de kip helemaal gaar is. Controleer het dikste deel met een voedselthermometer.'],
     'Pasta met tomaat', ['400 g pasta','600 g tomaten','2 tenen knoflook','30 ml olijfolie'],
     ['Laat tomaten en knoflook in olijfolie zacht sudderen.', 'Kook de pasta volgens de verpakking, giet af en meng met de saus.'],
     'Dagelijkse favorieten', 'Makkelijk', 'Ingrediënten'])

add('pl-PL',
    'Twój osobisty przepiśnik|Zaplanuj posiłki na tydzień|Zakupy z twojego planu|Gotuj krok po kroku|Sprawdzaj importowane przepisy|Zapasy pod kontrolą|Przepisy na tablecie|Czas na dobre jedzenie',
    'Przepisy, zdjęcia i notatki razem.|Dostosuj porcje i uporządkuj tydzień.|Łącz składniki i odliczaj zapasy.|Obsługa dotykowa, odczyt głosowy i minutniki.|Import Premium; rozpoznawanie zdjęć tylko po angielsku.|Ilości, terminy sprawdzenia i opcjonalne przypomnienia.|Czytaj zapisane przepisy bez Internetu.|Szablony i dodatkowe plany wymagają Premium.',
    'Wersja rozwojowa · Przykładowe przepisy · Zakupy wyłączone · Dostępność głosu jest zmienna',
    ['Kurczak z cytryną i ziołami', 'Złocisty kurczak z warzywami i nutą cytryny.',
     ['300 g kurczak','200 g ciecierzyca','150 g pomidory','1 cytryna','15 ml oliwa','1 cukinia'],
     ['Rozgrzej piekarnik do 200°C. Pokrój cukinię i pomidory.',
      'Włóż warzywa, ciecierzycę i kurczaka do naczynia. Dodaj oliwę i cytrynę.',
      'Piecz, aż kurczak będzie całkowicie upieczony. Sprawdź najgrubszą część termometrem kuchennym.'],
     'Makaron z pomidorami', ['400 g makaron','600 g pomidory','2 ząbki czosnku','30 ml oliwa'],
     ['Duś pomidory i czosnek w oliwie do miękkości.', 'Ugotuj makaron według opakowania, odcedź i wymieszaj z sosem.'],
     'Ulubione na co dzień', 'Łatwe', 'Składniki'])

add('pt-BR',
    'Seu livro de receitas|Planeje as refeições da semana|Compras a partir dos planos|Cozinhe passo a passo|Revise as importações|Sua despensa organizada|Receitas no seu tablet|Mais tempo para cozinhar',
    'Receitas, fotos e anotações juntas.|Ajuste porções e organize sua semana.|Some ingredientes e desconte os estoques.|Controles por toque, narração e temporizadores.|Importação Premium; fotos reconhecidas apenas em inglês.|Quantidades, datas de verificação e lembretes opcionais.|Leia receitas salvas sem conexão com a internet.|Modelos e planos extras exigem Premium.',
    'Prévia de desenvolvimento · Receitas de exemplo · Compras desativadas · A voz pode variar',
    ['Frango com limão e ervas', 'Frango dourado com legumes e um toque de limão.',
     ['300 g frango','200 g grão-de-bico','150 g tomates','1 limão','15 ml azeite','1 abobrinha'],
     ['Aqueça o forno a 200°C. Corte a abobrinha e os tomates.',
      'Coloque legumes, grão-de-bico e frango em uma assadeira. Acrescente azeite e limão.',
      'Asse até o frango estar completamente cozido. Verifique a parte mais grossa com um termômetro culinário.'],
     'Macarrão com tomate', ['400 g macarrão','600 g tomates','2 dentes alho','30 ml azeite'],
     ['Cozinhe os tomates e o alho no azeite em fogo baixo até ficarem macios.', 'Cozinhe o macarrão conforme a embalagem, escorra e misture ao molho.'],
     'Favoritos do dia a dia', 'Fácil', 'Ingredientes'])

add('pt-PT',
    'O seu livro de receitas|Planeie as refeições da semana|Compras a partir dos planos|Cozinhe passo a passo|Reveja as importações|A sua despensa organizada|Receitas no seu tablet|Mais tempo para cozinhar',
    'Receitas, fotografias e notas juntas.|Ajuste as porções e organize a semana.|Some ingredientes e deduza as existências.|Controlos táteis, narração e temporizadores.|Importação Premium; fotografias reconhecidas só em inglês.|Quantidades, datas de verificação e lembretes opcionais.|Leia receitas guardadas sem ligação à Internet.|Modelos e planos extra exigem Premium.',
    'Versão de desenvolvimento · Receitas de exemplo · Compras desativadas · A voz pode variar',
    ['Frango com limão e ervas', 'Frango dourado com legumes e um toque de limão.',
     ['300 g frango','200 g grão-de-bico','150 g tomates','1 limão','15 ml azeite','1 curgete'],
     ['Aqueça o forno a 200°C. Corte a curgete e os tomates.',
      'Coloque legumes, grão-de-bico e frango numa assadeira. Junte azeite e limão.',
      'Asse até o frango estar completamente cozinhado. Verifique a parte mais grossa com um termómetro alimentar.'],
     'Massa com tomate', ['400 g massa','600 g tomates','2 dentes alho','30 ml azeite'],
     ['Cozinhe os tomates e o alho no azeite em lume brando até ficarem macios.', 'Coza a massa segundo a embalagem, escorra e misture com o molho.'],
     'Favoritos do dia a dia', 'Fácil', 'Ingredientes'])

add('ar',
    'دفتر وصفاتك الشخصي|خطط لوجبات الأسبوع|تسوق حسب خطتك|اطبخ خطوة بخطوة|راجع الوصفات المستوردة|تابع مخزون مطبخك|وصفاتك على جهازك اللوحي|وقت أكثر لطعام طيب',
    'الوصفات والصور والملاحظات معًا.|عدّل الحصص ونظّم أسبوعك.|اجمع المكونات واطرح الكميات المتوفرة.|تحكم باللمس وتعليمات منطوقة ومؤقتات.|استيراد Premium؛ قراءة الصور للنص الإنجليزي فقط.|كميات ومواعيد مراجعة وتذكيرات اختيارية.|اقرأ الوصفات المحفوظة دون اتصال.|القوالب والخطط الإضافية تتطلب Premium.',
    'نسخة تطويرية · وصفات نموذجية · الشراء معطل · توفر الصوت يختلف',
    ['دجاج بالليمون والأعشاب', 'دجاج ذهبي مع الخضروات ولمسة من الليمون.',
     ['300 g دجاج','200 g حمص','150 g طماطم','1 ليمون','15 ml زيت زيتون','1 كوسا'],
     ['سخّن الفرن إلى 200 درجة مئوية. قطّع الكوسا والطماطم.',
      'ضع الخضروات والحمص والدجاج في صينية. أضف الزيت والليمون.',
      'اشوِ الدجاج حتى ينضج تمامًا. افحص أسمك جزء بمقياس حرارة الطعام.'],
     'معكرونة بالطماطم', ['400 g معكرونة','600 g طماطم','2 فص ثوم','30 ml زيت زيتون'],
     ['اطبخ الطماطم والثوم في زيت الزيتون على نار هادئة حتى تلين.', 'اسلق المعكرونة حسب العبوة ثم صفّها واخلطها بالصلصة.'],
     'أطباق يومية مفضلة', 'سهل', 'المكونات'])

add('hi',
    'आपकी अपनी रेसिपी बुक|हफ़्ते के भोजन की योजना|योजना से खरीदारी की सूची|कदम दर कदम खाना बनाएँ|आयात की गई रेसिपी जाँचें|रसोई के सामान पर नज़र|टैबलेट पर सहेजी रेसिपी|अच्छे खाने के लिए समय',
    'रेसिपी, फ़ोटो और नोट्स एक साथ।|परोसने की मात्रा बदलें और हफ़्ता व्यवस्थित करें।|सामग्री जोड़ें और उपलब्ध मात्रा घटाएँ।|टच नियंत्रण, बोले गए निर्देश और टाइमर।|आयात के लिए Premium; फ़ोटो में केवल अंग्रेज़ी पाठ की पहचान।|मात्रा, जाँच की तारीखें और वैकल्पिक रिमाइंडर।|बिना इंटरनेट सहेजी गई रेसिपी पढ़ें।|टेम्पलेट और अतिरिक्त योजनाओं के लिए Premium चाहिए।',
    'विकास संस्करण · नमूना रेसिपी · खरीदारी बंद · आवाज़ की उपलब्धता अलग हो सकती है',
    ['नींबू और जड़ी-बूटी वाला चिकन', 'सब्ज़ियों और नींबू के साथ सुनहरा चिकन।',
     ['300 g चिकन','200 g छोले','150 g टमाटर','1 नींबू','15 ml जैतून का तेल','1 ज़ुकीनी'],
     ['ओवन को 200°C पर गरम करें। ज़ुकीनी और टमाटर काटें।',
      'सब्ज़ियाँ, छोले और चिकन बेकिंग डिश में रखें। तेल और नींबू डालें।',
      'चिकन पूरी तरह पकने तक भूनें। सबसे मोटे हिस्से को फ़ूड थर्मामीटर से जाँचें।'],
     'टमाटर वाला पास्ता', ['400 g पास्ता','600 g टमाटर','2 कली लहसुन','30 ml जैतून का तेल'],
     ['टमाटर और लहसुन को जैतून के तेल में धीमी आँच पर नरम होने तक पकाएँ।', 'पैकेट के अनुसार पास्ता पकाएँ, पानी निकालें और सॉस में मिलाएँ।'],
     'रोज़ के पसंदीदा व्यंजन', 'आसान', 'सामग्री'])

if __name__ == '__main__':
    for locale, copy in data.items():
        assert len(copy['headlines']) == len(copy['subheadlines']) == 8, locale
    (ROOT/'marketing/visual-localization.json').write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(f'Prepared {len(data)} visual-localization drafts; native review pending.')
