# Cliente

mvn clean package

scp target/p2p-client-1.0-SNAPSHOT-fat.jar ceci@192.168.1.12:/home/ceci/Descargas

echo '27769ec3-6c91-4e79-a46a-fb34423690b8' > target/group.txt; scp target/group.txt ceci@192.168.1.12:/home/ceci/Descargas

java -Dserver=http://localhost:8080 -jar target/p2p-client-1.0-SNAPSHOT-fat.jar