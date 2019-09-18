ARG APP_INSIGHTS_AGENT_VERSION=2.5.0-BETA.5

FROM hmctspublic.azurecr.io/base/java:openjdk-8-distroless-1.0

COPY lib/applicationinsights-agent-${APP_INSIGHTS_AGENT_VERSION}.jar lib/AI-Agent.xml /opt/app/
COPY build/libs/sscs-evidence-share.jar /opt/app/

CMD ["sscs-evidence-share.jar"]

EXPOSE 8091
