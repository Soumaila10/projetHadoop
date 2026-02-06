#!/bin/bash
set -e

echo "Compiling RhymeFinder..."
mkdir -p build
javac -classpath $(hadoop classpath) -d build RhymeFinder.java
jar -cvf rhyme-finder.jar -C build/ .

echo "Preparing HDFS data..."
hdfs dfs -mkdir -p /user/etudiant/projetHadoop/input/rhymes
# Split common_words_en_subset.txt
split -n l/5 -d common_words_en_subset.txt part_v2_
hdfs dfs -put -f part_v2_* /user/etudiant/projetHadoop/input/rhymes/
rm part_v2_*

echo "Running MapReduce Job..."
hdfs dfs -rm -r /user/etudiant/projetHadoop/RhymeResult || true
hadoop jar rhyme-finder.jar RhymeFinder /user/etudiant/projetHadoop/input/rhymes /user/etudiant/projetHadoop/RhymeResult

echo "Output head:"
hdfs dfs -cat /user/etudiant/projetHadoop/RhymeResult/* | head -n 20
