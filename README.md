# Projet Hadoop MapReduce - 2026
**Auteur** : Soumaila
**Contact Référent** : Sergio Simonian
**Dossier** : `projetHadoop`

---

## 📌 Présentation du Projet
Ce projet consiste en la résolution de trois problèmes distincts d'analyse de données massives en utilisant le framework Hadoop MapReduce. L'objectif est de démontrer une capacité à manipuler des datasets variés (mots, transactions commerciales, données d'apprentissage OULAD) en optimisant les phases de Shuffle & Sort et de Partitionnement.

## 🛠 Structure du Répertoire
```bash
projetHadoop/
├── RhymeFinder.java            # Partie 1 : Détecteur de rimes
├── MarketBasketAnalysis.java   # Partie 2 : Analyse de panier
├── OuladRecommendation.java    # Partie 3 : Système de recommandation
├── run_project.sh              # Script d'automatisation (Partie 3)
├── generate_oulad_large.py     # Script de génération synthétique (OULAD)
├── generate_transactions.py    # Script de génération transactions
├── patterns/                   # Dossier KIs et Documentation
├── Resultats_Finaux/           # Sorties standard
├── Resultats_Gros_Volume/      # Sorties "Big Data"
├── LIVRABLES.md                # Documentation technique détaillée
└── README.md                   # Ce fichier
```

## 🚀 Instructions d'Exécution

### Partie 1 : Détecteur de Rimes (5 points)
**Objectif** : Identifier les mots de plus de 5 caractères partageant les 4 mêmes derniers caractères.
**Complexité** : O(n)

**Exécution** :
```bash
# Compilation
javac -classpath `hadoop classpath` -d build RhymeFinder.java
jar -cvf rhyme-finder.jar -C build/ .

# Split du fichier en 5 parties égales
split -n l/5 -d common_words_en_subset.txt part_

# Envoi sur HDFS
hdfs dfs -mkdir -p /user/etudiant/projetHadoop/input
hdfs dfs -put part_* /user/etudiant/projetHadoop/input/

# Lancement du Job
hdfs dfs -rm -r /user/etudiant/projetHadoop/RhymeResult
hadoop jar rhyme-finder.jar RhymeFinder /user/etudiant/projetHadoop/input /user/etudiant/projetHadoop/RhymeResult
```

### Partie 2 : Analyse du Panier (5 points)
**Objectif** : Extraire toutes les paires d'articles co-occurrentes et leurs transactions.
**Logique** : Utilisation d'un tri alphabétique des paires dans le Mapper pour éviter les doublons inversés.

**Exécution** :
```bash
# Compilation (Mise à jour du JAR)
javac -classpath `hadoop classpath` -d build MarketBasketAnalysis.java
jar -uvf rhyme-finder.jar -C build/ .

# Ingestion
wget https://cours.aiaoma.com/transactions.txt
hdfs dfs -put transactions.txt /user/etudiant/projetHadoop/input/

# Lancement du Job
hdfs dfs -rm -r /user/etudiant/projetHadoop/CommonItems
hadoop jar rhyme-finder.jar MarketBasketAnalysis /user/etudiant/projetHadoop/input/transactions.txt /user/etudiant/projetHadoop/CommonItems
```

### Partie 3 : Système de Recommandation (10 points)
**Ce pipeline est composé de 5 Jobs successifs :**
1.  **Join & Filter** : Jointure entre `student_vle` et `vle` sur le module DDD.
2.  **Aggregation** : Somme des clics par étudiant/activité.
3.  **Unique Features** : Extraction des colonnes de la matrice.
4.  **Pivot** : Transformation Long-to-Wide (Matrice creuse) via DistributedCache.
5.  **Scoring** : Calcul des recommandations (Map-only).

**Exécution Automatisée (Via Docker)** :

Ce projet est configuré pour tourner sur un environnement **Docker** local.

1.  **Démarrer le cluster** :
    ```powershell
    docker-compose up -d
    ```

2.  **Lancer le Pipeline Complet** :
    ```powershell
    # Connectez-vous au conteneur client
    docker exec -it hadoop-client bash
    
    # (Une fois dans le conteneur)
    cd /app
    ./run_project.sh
    ```

Le script `run_project.sh` se charge de :
*   Compiler les codes Java.
*   Ingérer les fichiers CSV (présents localement ou téléchargés).
*   Exécuter les 5 Jobs MapReduce.
*   Afficher les résultats du scoring. 

## ⚖️ Grille d'Évaluation (Auto-évaluation)
*   **Exactitude (50%)** : Solutions testées sur les datasets fournis, respect strict des critères de filtrage (ex: longueur > 5 pour les rimes, codes modules DDD).
*   **Qualité (50%)** :
    *   Code Java documenté et modulaire.
    *   Architecture O(n) pour les rimes.
    *   Gestion efficace du DistributedCache pour le Pivot.
    *   Scripts shell fournis pour la reproductibilité.

## 📝 Démarches Suivies
*   **Rimes** : Utilisation du suffixe comme clé de partitionnement pour forcer le regroupement naturel des mots qui riment lors du Shuffle.
*   **Panier** : Algorithme de calcul de paires (n*(n-1)/2) par transaction avec normalisation des clés (tri).
*   **Recommandation** : Gestion des valeurs manquantes (clics = 0) lors du pivot pour assurer une matrice dense en sortie.

## 📈 Scalabilité et Gros Volumes
Le projet a été testé avec succès sur un jeu de données étendu pour prouver la robustesse de l'implémentation Hadoop.



### Résultats
L'exécution produit les résultats dans le dossier `Resultats_Finaux`.

## 📂 Accès à HDFS (Hadoop Distributed File System)

### Option 1 : Ligne de Commande (CLI)
Via le conteneur Docker `hadoop-client` :

```bash
# Lister les fichiers du projet
docker exec hadoop-client hdfs dfs -ls -R /user/etudiant/projetHadoop

# Lire le début d'un fichier (ex: résultat rimes)
docker exec hadoop-client hdfs dfs -head /user/etudiant/projetHadoop/RhymeResult/part-r-00000

# Shell interactif
docker exec -it hadoop-client bash
# (Dedans) hdfs dfs -ls /
```

### Option 2 : Interface Web (NameNode UI)
Si les ports sont ouverts, accédez à l'interface graphique :
*   **URL** : [http://localhost:9870](http://localhost:9870)
*   **Navigation** : Menu "Utilities" > "Browse the file system".

### Chemins Clés
*   **Input** : `/user/etudiant/projetHadoop/input`
*   **Output Rimes** : `/user/etudiant/projetHadoop/RhymeResult`
*   **Output Panier** : `/user/etudiant/projetHadoop/CommonItems`
*   **Output OULAD** : `/user/etudiant/projetHadoop/oulad_output/`
