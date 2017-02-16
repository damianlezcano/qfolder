# qfolder

1) Generamos el jar

	mvn clean package

2) Ejecutamos la aplicación

	java -Dapp.name=Damian -Dapp.rest.port=9999 -Dapp.hosts=http://localhost:9876 -Dapp.shell.browser=chrome -jar qfolder.jar start

Para mas información

	java -jar qfolder.jar help