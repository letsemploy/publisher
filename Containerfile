FROM docker.io/eclipse-temurin:25-jre-alpine
COPY ./target/app.jar /opt/app/
# Memory defaults for a small instance, measured in 512 MB and 1 GB containers
# (CLAUDE.md, "Memory"). Serial GC, pinned: the JVM would pick G1 from 2 GB up,
# which measured larger. Startup needs far more heap than the application uses
# afterwards, so the cap is half the container - the default quarter cannot even
# start in 512 MB - and the free ratios shrink the heap back once startup is done.
# Compact object headers shrink every object. Setting JAVA_TOOL_OPTIONS in a
# deployment replaces all of these.
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:MaxRAMPercentage=50 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -XX:ReservedCodeCacheSize=64m -XX:+UseCompactObjectHeaders"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/opt/app/app.jar"]
