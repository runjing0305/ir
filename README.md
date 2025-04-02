## How to call Java interface of Gurobi ?
- Step 1: Install gurobi.jar into Maven repo:
  - ```mvn install:install-file -Dfile=/opt/gurobi952/linux64/lib/gurobi.jar -DgroupId=gurobi -DartifactId=gurobi -Dversion=9.5.2 -Dpackaging=jar```
- Step 2: Load Gurobi jar
  - pom.xml -> Maven -> Reload project
- Step 3: Debug and Run project in IDEA (make sure that license of Gurobi has been installed)

- Step 4: When compiling and want to obtain an executable jar:
  - ```mvn clean compile assembly:single```
  - ```java -jar target/RAS-1.0-SNAPSHOT-jar-with-dependencies.jar```