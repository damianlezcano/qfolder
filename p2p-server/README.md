# Server Quarkus con tecnologia SSE

mvn package quarkus:dev


Desplegar en openshift

oc new-project qfolder

oc new-build --strategy docker --dockerfile - --code . --name p2p-server < src/main/docker/Dockerfile.jvm

---

mvn clean package

oc start-build --from-dir . p2p-server

oc new-app --image-stream p2p-server --name p2p-server

oc expose service/p2p-server







https://stackoverflow.com/questions/39861900/resteasy003145-unable-to-find-a-messagebodyreader-of-content-type-application-j