# Guide d'Installation et Test Local (Docker)

Comme Hadoop n'est pas installé sur votre machine Windows, nous allons utiliser **Docker** pour créer un mini-cluster Hadoop virtuel en quelques secondes.

## 1. Pré-requis
Vous avez **Docker Desktop** et **WSL 2** installés (détectés sur votre machine), c'est parfait.

## 2. Démarrer Hadoop
Assurez-vous d'être dans le dossier `C:\Users\Soumaila\MesApplications\projetHadoop`.

1.  Ouvrez un terminal (PowerShell ou CMD).
2.  Lancez le cluster :
    ```powershell
    docker-compose up -d
    ```
    *Cela va télécharger les images et démarrer 5 conteneurs (Namenode, Datanode, ResourceManager... et un client).*

3.  Vérifiez que tout tourne :
    ```powershell
    docker ps
    ```

## 3. Tester le Projet

Nous allons exécuter toutes les commandes à l'intérieur du conteneur `hadoop-client` qui a accès à vos fichiers locaux (via le volume `/app`).

### a. Se connecter au conteneur client
```powershell
docker exec -it hadoop-client bash
```
*(Vous êtes maintenant dans un terminal Linux "dans" le cluster, dossier `/`)*

### b. Aller dans le dossier du projet
```bash
cd /app
# Vous devriez voir vos fichiers Java ici (ls -l)
```

### c. Lancer les Tests (Exemple Rapide - Partie 1)

**IMPORTANT** : Dans ce conteneur, `JAVA_HOME` et `HADOOP_CLASSPATH` sont déjà bien configurés.

```bash
# 1. Compilation
mkdir -p build
javac -cp `hadoop classpath` -d build RhymeFinder.java
jar -cvf rhyme-finder.jar -C build/ .

# 2. Préparer les données
# Si vous n'avez pas le fichier txt, créez-en un petit pour tester :
echo -e "running\nplanning\nwinning\nswimming\napple\nbanana" > small_test.txt

# 3. HDFS
hdfs dfs -mkdir -p /user/etudiant/projetHadoop/input
hdfs dfs -put small_test.txt /user/etudiant/projetHadoop/input/

# 4. Exécuter
hadoop jar rhyme-finder.jar RhymeFinder /user/etudiant/projetHadoop/input /user/etudiant/projetHadoop/output

# 5. Voir résultat
hdfs dfs -cat /user/etudiant/projetHadoop/output/*
```

### d. Lancer le Script Global (Partie 3)
```bash
# S'assurer que le script est au format Unix
sed -i 's/\r$//' run_project.sh
chmod +x run_project.sh

# Lancer
./run_project.sh
```

## 4. Arrêter Hadoop
Une fois fini :
```powershell
exit # pour sortir du conteneur
docker-compose down
```
