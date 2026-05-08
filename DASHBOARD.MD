🚨 Ligne 1 : KPI P1 - SANTÉ CRITIQUE & LATENCE
Ces indicateurs sont les "voyants rouges" de votre tableau de bord. S'ils s'allument, le cluster est en danger.

Under Replicated (Partitions sous-répliquées)
Ce que c'est : Le nombre de partitions qui n'ont pas leur compte complet de copies synchronisées (ex: on demande 3 copies, mais seulement 2 sont à jour).
L'explication : Si ce chiffre est supérieur à 0, cela signifie qu'un de vos 3 brokers est tombé, est redémarré, ou est tellement surchargé qu'il n'arrive plus à copier les données assez vite. S'il ne redescend pas à 0 après quelques secondes/minutes, vous avez une panne matérielle ou réseau.
Offline Partitions (Partitions hors-ligne)
Ce que c'est : Le pire cauchemar sur Kafka. Ce sont les partitions qui n'ont plus aucun broker valide pour élire un "Leader".
L'explication : Toute lecture ou écriture sur ces partitions est bloquée. Vos applications Quarkus vont commencer à lancer des exceptions. Cela arrive généralement si 2 ou 3 brokers tombent en même temps.
Active Controller
Ce que c'est : Dans un cluster KRaft, c'est le broker qui agit comme le "cerveau" (il gère les métadonnées de tout le monde).
L'explication : Ce chiffre doit toujours être exactement égal à 1. Si c'est 0, votre cluster n'a plus de cerveau (impossible de créer des topics ou de faire des élections). Si c'est 2, vous avez un "split-brain" (gros problème réseau).
Latence Produce (P99)
Ce que c'est : Le temps maximum qu'il a fallu pour traiter 99% des messages envoyés par vos producteurs.
L'explication : C'est la mesure de fluidité par excellence. Lors d'un test modéré, elle doit rester sous les 50-100ms. Lors d'un stress test massif, elle va monter. Si elle dépasse 1000ms, cela signifie que vos disques durs ou votre réseau n'arrivent plus à absorber le débit.
🚀 Ligne 2 : DÉBIT GLOBAL & PAR BROKER / TOPIC
Cette section montre la "vélocité" de votre cluster (Combien de données transitent ?).

Débit Global (Messages/sec)
Ce que c'est : La somme totale des messages ingérés chaque seconde.
L'explication : Permet de valider la puissance de vos tirs de performance. C'est ici que vous verrez si vous avez atteint les 10k, 50k ou 100k messages par seconde.
Production par Broker (Messages/sec)
Ce que c'est : Le débit précédent, divisé entre vos 3 brokers (tuuuur-kafka-1, 2, 3).
L'explication : Permet de vérifier la bonne répartition de la charge. Les 3 lignes doivent se superposer. Si un broker prend 80% du trafic, c'est que vos partitions sont mal réparties.
Production par Topic (Messages/sec)
Ce que c'est : Le volume d'envoi par sujet métier (ex: orders-events vs stock-events).
L'explication : Si vous remarquez que orders-events tourne à 10k msg/s mais que stock-events est à 0, cela indique un bug dans votre code (la Saga s'est bloquée au milieu et l'application n'émet plus la suite !).
Bytes In / Out par Broker (B/s)
Ce que c'est : La bande passante réseau. En positif (IN) ce qui rentre, en négatif (OUT) ce qui sort (lectures des consommateurs et copies de réplication).
L'explication : Très utile pour vérifier la saturation de votre carte réseau. Le OUT est souvent plus gros que le IN, car un message écrit une fois (IN) peut être lu par plusieurs Consumer Groups différents (OUT multiples).
🐌 Consumer Group Lag (P1)
Ce que c'est : Le retard (en nombre de messages) de vos consommateurs Quarkus par rapport aux producteurs.
L'explication : C'est LE graphique le plus important pour vos développeurs. Si cette courbe monte en flèche, cela signifie que vos producteurs écrivent trop vite par rapport à la vitesse de traitement de vos consommateurs (par exemple, le PaymentService qui a un Thread.sleep(1000)). Pour faire baisser cette courbe, la solution est de lancer de nouvelles instances de l'application Quarkus !
⚖️ Ligne 3 : RÉPARTITION, LEADERSHIP & ISR
Ces graphiques montrent l'équilibrage interne des données.

Leader Count / Partition Count par Broker
Ce que c'est : Combien de fois chaque broker est le point d'entrée principal (Leader) et combien il stocke de morceaux de données au total (Partition).
L'explication : C'est une vérification de l'équilibre. Si un broker devient le leader de toutes les partitions, c'est lui qui fera 100% du travail CPU/Réseau. Les 3 brokers doivent être plus ou moins égaux.
Déséquilibre de Taille par Topic (Skew)
Ce que c'est : La différence de remplissage entre la partition la plus grosse et la partition la plus petite d'un topic.
L'explication : Indique un problème de "clé de partitionnement". Si tout le monde achète le même objet (itemId utilisé comme clé), une seule partition va accumuler toute la donnée pendant que les autres seront vides. Ce graphique pointe directement vers un problème de design applicatif.
☕ Ligne 4 : JVM & RÉPLICATION
Monitoring bas-niveau des serveurs Kafka (qui sont des applications Java).

Utilisation Mémoire Heap (%)
Ce que c'est : La RAM allouée utilisée par la machine virtuelle Java de Kafka.
L'explication : Kafka stocke les messages sur le disque (via le cache de l'OS) et très peu dans la Heap Java. Si cette courbe monte à 100%, c'est souvent parce que les connexions réseau ou les requêtes accumulées explosent (trop de clients connectés ou de grosses requêtes bloquées).
Temps Garbage Collector (rate)
Ce que c'est : Le temps passé par Java à "faire le ménage" dans sa mémoire (Garbage Collection).
L'explication : Si ce graphique montre de gros pics (ex: 500ms par seconde passées à nettoyer), le broker est littéralement figé pendant ce temps. Ça explique instantanément les pics dans le graphique "Latence Produce (P99)".
Changements ISR (In-Sync Replicas)
Ce que c'est : La fréquence à laquelle des brokers sont éjectés du groupe de réplication valide (Shrink) ou l'intègrent à nouveau (Expand).
L'explication : Si ce graphique fait le "yoyo" tout le temps, cela veut dire que vos brokers souffrent. Ils sont tellement lents sous la charge du test (ou à cause de GC Pauses) qu'ils n'arrivent plus à se synchroniser à temps, Kafka les exclut, puis ils rattrapent leur retard, sont réintégrés, puis exclus à nouveau.