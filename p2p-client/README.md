# Cliente

mvn clean package

scp target/p2p-client-1.0-SNAPSHOT-fat.jar ceci@192.168.1.12:/home/ceci/Descargas

echo '21b31e13-c97b-4192-a2ab-e8613d9cac23' > target/group.txt; scp target/group.txt ceci@192.168.1.12:/home/ceci/Descargas

java -Dserver=http://localhost:8080 -jar target/p2p-client-1.0-SNAPSHOT-fat.jar