@echo off
setlocal enabledelayedexpansion

REM Check if jooq is the first parameter
if "%1"=="jooq" (
    echo Building commons...
    cd commons
    call mvn clean install
    if errorlevel 1 (
        echo Error: Maven build failed for commons
        exit /b 1
    )

    echo Building commons2...
    cd ..\commons2
    call mvn clean install
    if errorlevel 1 (
        echo Error: Maven build failed for commons2
        exit /b 1
    )

    echo Building commons-jooq...
    cd ..\commons-jooq
    call mvn clean install
    if errorlevel 1 (
        echo Error: Maven build failed for commons-jooq
        exit /b 1
    )

    echo Building commons2-jooq...
    cd ..\commons2-jooq
    call mvn clean install
    if errorlevel 1 (
        echo Error: Maven build failed for commons2-jooq
        exit /b 1
    )

    echo Building commons-security...
    cd ..\commons-security
    call mvn clean install
    if errorlevel 1 (
        echo Error: Maven build failed for commons-security
        exit /b 1
    )

    echo Building commons2-security...
    cd ..\commons2-security
    call mvn clean install
    if errorlevel 1 (
        echo Error: Maven build failed for commons2-security
        exit /b 1
    )

    echo Building commons-mongo...
    cd ..\commons-mongo
    call mvn clean install
    if errorlevel 1 (
        echo Error: Maven build failed for commons-mongo
        exit /b 1
    )

    echo Building commons-mq...
    cd ..\commons-mq
    call mvn clean install
    if errorlevel 1 (
        echo Error: Maven build failed for commons-mq
        exit /b 1
    )

    echo Building commons2-mq...
    cd ..\commons2-mq
    call mvn clean install
    if errorlevel 1 (
        echo Error: Maven build failed for commons2-mq
        exit /b 1
    )

    echo Building commons-core...
    cd ..\commons-core
    call mvn clean install -Pjooq
    if errorlevel 1 (
        echo Error: Maven build failed for commons-core
        exit /b 1
    )

    echo Building security...
    cd ..\security
    call mvn clean install -Pjooq
    if errorlevel 1 (
        echo Error: Maven build failed for security
        exit /b 1
    )

    echo Building entity-collector...
    cd ..\entity-collector
    call mvn clean install -Pjooq
    if errorlevel 1 (
        echo Error: Maven build failed for entity-collector
        exit /b 1
    )

    echo Building entity-processor...
    cd ..\entity-processor
    call mvn clean install -Pjooq
    if errorlevel 1 (
        echo Error: Maven build failed for entity-processor
        exit /b 1
    )

    echo Building files...
    cd ..\files
    call mvn clean install -Pjooq
    if errorlevel 1 (
        echo Error: Maven build failed for files
        exit /b 1
    )

    echo Building message...
    cd ..\message
    call mvn clean install -Pjooq
    if errorlevel 1 (
        echo Error: Maven build failed for message
        exit /b 1
    )

    echo Building multi...
    cd ..\multi
    call mvn clean install -Pjooq
    if errorlevel 1 (
        echo Error: Maven build failed for multi
        exit /b 1
    )

    echo Building notification...
    cd ..\notification
    call mvn clean install -Pjooq
    if errorlevel 1 (
        echo Error: Maven build failed for notification
        exit /b 1
    )

    echo JOOQ build completed successfully!
    exit /b 0
)

REM Regular build process
echo Building commons...
cd commons
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for commons
    exit /b 1
)

echo Building commons2...
cd ..\commons2
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for commons2
    exit /b 1
)

echo Building commons-jooq...
cd ..\commons-jooq
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for commons-jooq
    exit /b 1
)

echo Building commons2-jooq...
cd ..\commons2-jooq
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for commons2-jooq
    exit /b 1
)

echo Building commons-security...
cd ..\commons-security
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for commons-security
    exit /b 1
)

echo Building commons2-security...
cd ..\commons2-security
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for commons2-security
    exit /b 1
)

echo Building commons-mongo...
cd ..\commons-mongo
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for commons-mongo
    exit /b 1
)

echo Building commons-mq...
cd ..\commons-mq
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for commons-mq
    exit /b 1
)

echo Building commons2-mq...
cd ..\commons2-mq
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for commons2-mq
    exit /b 1
)

echo Building commons-core...
cd ..\commons-core
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for commons-core
    exit /b 1
)

echo Building config...
cd ..\config
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for config
    exit /b 1
)

echo Building core...
cd ..\core
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for core
    exit /b 1
)

echo Building eureka...
cd ..\eureka
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for eureka
    exit /b 1
)

echo Building gateway...
cd ..\gateway
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for gateway
    exit /b 1
)

echo Building security...
cd ..\security
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for security
    exit /b 1
)

echo Building files...
cd ..\files
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for files
    exit /b 1
)

echo Building entity-processor...
cd ..\entity-processor
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for entity-processor
    exit /b 1
)

echo Building entity-collector...
cd ..\entity-collector
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for entity-collector
    exit /b 1
)

echo Building notification...
cd ..\notification
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for notification
    exit /b 1
)

echo Building message...
cd ..\message
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for message
    exit /b 1
)

echo Building multi...
cd ..\multi
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for multi
    exit /b 1
)

echo Building ui...
cd ..\ui
call mvn %*
if errorlevel 1 (
    echo Error: Maven build failed for ui
    exit /b 1
)

echo Build completed successfully!
exit /b 0
