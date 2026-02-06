#!/bin/bash

# Configuration
HADOOP_CLASSPATH=$(hadoop classpath)
BUILD_DIR="build_oulad"
JAR_NAME="oulad-reco.jar"

# --- Partie 1 : Rimes ---
INPUT_RHYME="/user/etudiant/projetHadoop/input/rhymes"
OUTPUT_RHYME="/user/etudiant/projetHadoop/RhymeResult"

# --- Partie 2 : Panier ---
INPUT_BASKET="/user/etudiant/projetHadoop/input"
OUTPUT_BASKET="/user/etudiant/projetHadoop/CommonItems"

# --- Partie 3 : OULAD ---
INPUT_DIR="/user/etudiant/projetHadoop/oulad_input"
OUTPUT_BASE="/user/etudiant/projetHadoop/oulad_output"

echo "=== 1. Compilation & Jar ==="
mkdir -p $BUILD_DIR
# Compile toutes les classes
javac -classpath "$HADOOP_CLASSPATH" -d $BUILD_DIR *.java
if [ $? -ne 0 ]; then
    echo "Erreur de compilation"
    exit 1
fi
jar -cvf $JAR_NAME -C $BUILD_DIR .


echo "=== 2. Préparation HDFS Globale ==="
# Partie 1 : Rimes
echo "Préparation Rimes (common_words_en_subset.txt)..."
hdfs dfs -rm -r $INPUT_RHYME || true
hdfs dfs -mkdir -p $INPUT_RHYME
split -n l/5 -d common_words_en_subset.txt part_v2_
hdfs dfs -put -f part_v2_* $INPUT_RHYME/
rm part_v2_*

# Partie 2 : Transactions
echo "Préparation Panier (transactions.txt)..."
hdfs dfs -mkdir -p $INPUT_BASKET
hdfs dfs -put -f transactions.txt $INPUT_BASKET/transactions.txt

# Partie 3 : OULAD (Dataset Original)
echo "Préparation OULAD (studentVle.csv, vle.csv)..."
hdfs dfs -rm -r $INPUT_DIR || true
hdfs dfs -mkdir -p $INPUT_DIR

# 1. Remove headers (locally)
echo "Suppression des en-têtes..."
tail -n +2 studentVle.csv > studentVle_clean.csv
tail -n +2 vle.csv > vle_clean.csv

# 2. Split studentVle into 4 parts
echo "Découpage studentVle.csv..."
mkdir -p input_oulad_splits
split -n l/4 -d studentVle_clean.csv input_oulad_splits/part_

echo "Upload séquentiel..."
for f in input_oulad_splits/*; do
    echo "Uploading $f..."
    hdfs dfs -put -f "$f" $INPUT_DIR/
done
hdfs dfs -put -f vle_clean.csv $INPUT_DIR/vle.csv
rm -rf input_oulad_splits studentVle_clean.csv vle_clean.csv


echo "=== 3. Exécution Partie 1 (Rimes) ==="
hdfs dfs -rm -r $OUTPUT_RHYME
hadoop jar $JAR_NAME RhymeFinder $INPUT_RHYME $OUTPUT_RHYME


echo "=== 4. Exécution Partie 2 (Panier) ==="
hdfs dfs -rm -r $OUTPUT_BASKET
hadoop jar $JAR_NAME MarketBasketAnalysis $INPUT_BASKET/transactions.txt $OUTPUT_BASKET


echo "=== 5. Exécution Partie 3 (OULAD) ==="
hdfs dfs -rm -r $OUTPUT_BASE
hadoop jar $JAR_NAME OuladRecommendation $INPUT_DIR/part_* $INPUT_DIR/vle.csv $OUTPUT_BASE


echo "=== 6. Export Résultats ==="
EXPORT_DIR="Resultats_Finaux"
mkdir -p $EXPORT_DIR

# Nettoyage
rm -f $EXPORT_DIR/*

echo "Export Partie 1 (Rimes)..."
hdfs dfs -getmerge $OUTPUT_RHYME $EXPORT_DIR/1_Resultats_Rimes.txt

echo "Export Partie 2 (Panier)..."
hdfs dfs -getmerge $OUTPUT_BASKET $EXPORT_DIR/2_Resultats_Panier.txt

echo "Export Partie 3 (OULAD)..."
hdfs dfs -getmerge $OUTPUT_BASE/job5_scoring $EXPORT_DIR/3_Resultats_Recommandations.txt

echo "=== SUCCÈS GLOBAL ==="
echo "Les résultats sont dans : $EXPORT_DIR"

echo ""
echo "--- Résultats Partie 1 (Rimes) ---"
hdfs dfs -cat $OUTPUT_RHYME/*
echo ""
echo "--- Résultats Partie 2 (Panier) ---"
hdfs dfs -cat $OUTPUT_BASKET/*
echo ""
echo "--- Résultats Partie 3 (OULAD Scoring) ---"
hdfs dfs -cat $OUTPUT_BASE/job5_scoring/part-m-00000


