#!/bin/bash

# Script para ejecutar GeneracionXmlService con todas las dependencias

# Compilar el proyecto
echo "Compilando proyecto..."
mvn compile -DskipTests -DskipFlyway=true -Dmaven.resources.skip=true -Dmaven.clean.skip=true -q

# Ejecutar la clase con todas las dependencias
echo "Ejecutando GeneracionXmlService..."
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" com.franco.dev.service.sifen.service.GeneracionXmlService
