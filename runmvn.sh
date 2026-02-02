#!/bin/bash
set -e  # Exit immediately if a command exits with a non-zero status

# if jooq is the first parameter
if [ "$1" == "jooq" ]; then
    echo "Building commons..."
    cd ./commons
    mvn clean install
    echo "Building commons2..."
    cd ../commons2
    mvn clean install
    echo "Building commons-jooq..."
    cd ../commons-jooq
    mvn clean install
    echo "Building commons2-jooq..."
    cd ../commons2-jooq
    mvn clean install
    echo "Building commons-security..."
    cd ../commons-security
    mvn clean install
    echo "Building commons2-security..."
    cd ../commons2-security
    mvn clean install
    echo "Building commons-mongo..."
    cd ../commons-mongo
    mvn clean install
    echo "Building commons-mq..."
    cd ../commons-mq
    mvn clean install
    echo "Building commons2-mq..."
    cd ../commons2-mq
    mvn clean install
    echo "Building commons-core..."
    cd ../commons-core
    mvn clean install -Pjooq
    echo "Building security..."
    cd ../security
    mvn clean install -Pjooq
    echo "Building entity-collector..."
    cd ../entity-collector
    mvn clean install -Pjooq
    echo "Building entity-processor..."
    cd ../entity-processor
    mvn clean install -Pjooq
    echo "Building files..."
    cd ../files
    mvn clean install -Pjooq
    echo "Building message..."
    cd ../message
    mvn clean install -Pjooq
    echo "Building multi..."
    cd ../multi
    mvn clean install -Pjooq
    echo "Building notification..."
    cd ../notification
    mvn clean install -Pjooq
    echo "JOOQ build completed successfully!"
    exit 0
fi

echo "Building commons..."
cd ./commons
mvn $@

echo "Building commons2..."
cd ../commons2
mvn $@

echo "Building commons-jooq..."
cd ../commons-jooq
mvn $@

echo "Building commons2-jooq..."
cd ../commons2-jooq
mvn $@

echo "Building commons-security..."
cd ../commons-security
mvn $@

echo "Building commons2-security..."
cd ../commons2-security
mvn $@

echo "Building commons-mongo..."
cd ../commons-mongo
mvn $@

echo "Building commons-mq..."
cd ../commons-mq
mvn $@

echo "Building commons2-mq..."
cd ../commons2-mq
mvn $@

echo "Building commons-core..."
cd ../commons-core
mvn $@

echo "Building config..."
cd ../config
mvn $@

echo "Building core..."
cd ../core
mvn $@

echo "Building eureka..."
cd ../eureka
mvn $@

echo "Building gateway..."
cd ../gateway
mvn $@

echo "Building security..."
cd ../security
mvn $@

echo "Building files..."
cd ../files
mvn $@

echo "Building entity-processor..."
cd ../entity-processor
mvn $@

echo "Building entity-collector..."
cd ../entity-collector
mvn $@

echo "Building notification..."
cd ../notification
mvn $@

echo "Building message..."
cd ../message
mvn $@

echo "Building multi..."
cd ../multi
mvn $@

echo "Building ui..."
cd ../ui
mvn $@

echo "Build completed successfully!"
