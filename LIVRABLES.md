# Livrables - Projet Hadoop RhymeFinder

## 1. Commandes Shell Requises

### a. Découpage du fichier en 5 parties égales (Split)
Nous utilisons la commande `split` pour diviser le fichier. L'option `-n l/5` découpe en 5 parties basées sur les lignes (pour ne pas couper les mots).
```bash
# Assurez-vous d'être dans le répertoire contenant common_words_en_subset.txt
# Création d'un dossier pour les splits
mkdir input_splits

# Découpage en 5 fichiers (préfixe 'part_')
split -n l/5 -d common_words_en_subset.txt input_splits/part_

# Vérification
ls -l input_splits/
```

### b. Ingestion HDFS
```bash
# Création du répertoire sur HDFS
hdfs dfs -mkdir -p /user/etudiant/projetHadoop/input

# Importation des fichiers splittés dans HDFS
hdfs dfs -put input_splits/* /user/etudiant/projetHadoop/input/

# Vérification
hdfs dfs -ls /user/etudiant/projetHadoop/input/
```

### c. Compilation et Création du JAR
Supposons que `HADOOP_HOME` est défini. Nous devons inclure les bibliothèques Hadoop dans le classpath.
```bash
# Création du dossier de build
mkdir build

# Compilation
javac -classpath `hadoop classpath` -d build RhymeFinder.java

# Création du JAR
jar -cvf rhyme-finder.jar -C build/ .
```

### d. Exécution du Job
```bash
# Suppression du dossier de sortie s'il existe déjà
hdfs dfs -rm -r /user/etudiant/projetHadoop/output

# Exécution
hadoop jar rhyme-finder.jar RhymeFinder /user/etudiant/projetHadoop/input /user/etudiant/projetHadoop/output
```

### e. Lecture du résultat
```bash
hdfs dfs -cat /user/etudiant/projetHadoop/output/*
```

---

## 2. Démarche et Justification Technique

### Choix de la Clé de Partitionnement (Suffixe de 4 caractères)
*   **Scalabilité & Regroupement Naturel** : En choisissant les 4 derniers caractères comme clé (`Output Key` du Mapper), nous déléguons au framework MapReduce la tâche complexe de tri et de regroupement (Shuffle & Sort).
*   **Complexité O(n)** :
    *   Le Mapper parcourt chaque mot une seule fois : O(n).
    *   Le Shuffle & Sort regroupe toutes les clés identiques.
    *   Le Reducer parcourt chaque groupe de mots une seule fois.
    *   Il n'y a **aucune comparaison croisée** (type double boucle for) entre les mots eux-mêmes, ce qui garantit une performance linéaire par rapport au volume de données.

### Optimisations Implémentées
*   **Filtrage Précoce (Early Filtering)** : Si un mot a une longueur $\le 5$, il est immédiatement ignoré dans le Mapper. Cela évite d'envoyer des données inutiles sur le réseau vers les Reducers (IO réseau réduit) et économise de l'espace disque intermédiaire.
*   **Combiner (Analysé)** :
    *   Bien qu'un Combiner soit souvent utile pour faire des sommes (WordCount), ici notre Reducer doit lister des mots spécifiques. Un Combiner qui concaténerait des chaînes de caractères pourrait réduire le nombre d'objets, mais augmenterait la complexité de gestion des types (Text vs Liste) pour un gain de performance marginal sur des chaînes simples. Pour la clarté et la robustesse du code ("Propreté maximale"), l'architecture Mapper-Reducer directe a été privilégiée, laissant le Shuffle gérer le groupement efficacement.

### Architecture du Code
*   **Classe Unique** : `RhymeFinder` encapsule `RhymeMapper` et `RhymeReducer` comme classes statiques internes, simplifiant le déploiement (un seul fichier source).
*   **Types Hadoop** : Utilisation stricte de `LongWritable` et `Text` pour la sérialisation efficace via le framework Hadoop.

---

## 3. Partie 2 : Analyse du Panier (Market Basket Analysis)

### a. Commandes Shell Requises

#### Téléchargement du Dataset
```bash
wget https://cours.aiaoma.com/transactions.txt
```

#### Ingestion HDFS
```bash
# Ingestion
hdfs dfs -put transactions.txt /user/etudiant/projetHadoop/input/

# Vérification
hdfs dfs -ls /user/etudiant/projetHadoop/input/
```

#### Compilation et Exécution
```bash
# Compilation du nouveau fichier
javac -classpath `hadoop classpath` -d build MarketBasketAnalysis.java

# Mise à jour du JAR
jar -uvf rhyme-finder.jar -C build/ .

# Exécution (Attention : on change de Driver class et de dossier output)
hdfs dfs -rm -r /user/etudiant/projetHadoop/output_market
hadoop jar rhyme-finder.jar MarketBasketAnalysis /user/etudiant/projetHadoop/input/transactions.txt /user/etudiant/projetHadoop/output_market
```

### b. Justification du Tri dans le Mapper
*   **Problème des Doublons** : Dans l'analyse de paires, la relation est symétrique. Avoir "Milk" et "Bread" dans le même panier signifie la même chose que "Bread" et "Milk". Sans précaution, nous générerions deux clés différentes : `(Milk, Bread)` et `(Bread, Milk)`.
*   **Solution (Tri Alphabétique)** : En triant systématiquement les deux articles d'une paire avant de créer la clé (ex: `min(A,B) + "," + max(A,B)`), nous garantissons que **quelle que soit l'ordre d'apparition** dans la transaction d'origine, la clé émise vers le Reducer sera **toujours identique** (ex: toujours "Bread,Milk").
*   **Impact** : Cela permet au Reducer de recevoir TOUTES les occurrences de cette association sous une seule et même clé, assurant une agrégation correcte et complète des IDs de transaction. Sans ce tri, les données seraient fragmentées en deux groupes distincts, faussant l'analyse.

---

## 4. Partie 3 : Système de Recommandation OULAD

### a. Pipeline de 5 Jobs (Explication)
*   **Job 1 (Join)** : Filtre les données pour le module 'DDD' et joint `student_vle` avec `vle` pour obtenir les détails des activités.
*   **Job 2 (Aggregation)** : Calcule le nombre total de clics par étudiant et par activité.
*   **Job 3 (Features)** : Extrait la liste *unique* des activités (colonnes) et calcule le total global des clics par activité (requis pour le scoring).
*   **Job 4 (Pivot)** : Utilise le `DistributedCache` pour lire les colonnes (Job 3) et pivoter les données de chaque étudiant en un vecteur dense.
*   **Job 5 (Scoring)** : Map-only job. Pour chaque étudiant, calcule le score de recommandation pour les activités non visitées en se basant sur la popularité globale (extraite du cache) et l'activité totale de l'étudiant.

### b. Commandes d'Exécution (Environnement Docker)

Le script `run_project.sh` automatise tout le processus. Comme nous utilisons Docker, les fichiers doivent être disponibles dans le volume monté `/app`.

```bash
# 1. Démarrer Docker
docker-compose up -d

# 2. Entrer dans le conteneur
docker exec -it hadoop-client bash

# 3. Lancer le projet (dans le conteneur)
cd /app
chmod +x run_project.sh
./run_project.sh
```

### c. Extrait du script `run_project.sh`
```bash
# ... Compilation ...
hadoop jar oulad-reco.jar OuladRecommendation /user/etudiant/projetHadoop/oulad_input/studentVle.csv /user/etudiant/projetHadoop/oulad_input/vle.csv /user/etudiant/projetHadoop/oulad_output
```


