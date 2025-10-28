# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS base

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       wget \
       unzip \
       libstdc++6 \
       python3 \
    && rm -rf /var/lib/apt/lists/*

# Install Android command line tools
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools \
    && cd /tmp \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q commandlinetools-linux-11076708_latest.zip \
    && mkdir -p $ANDROID_SDK_ROOT/cmdline-tools/latest \
    && mv cmdline-tools/* $ANDROID_SDK_ROOT/cmdline-tools/latest/ \
    && rm -rf commandlinetools-linux-11076708_latest.zip cmdline-tools

# Accept licenses and install the SDK components required for unit tests
RUN yes | sdkmanager --licenses >/dev/null \
    && yes | sdkmanager \
       "platforms;android-34" \
       "build-tools;34.0.0" \
       "platform-tools"

WORKDIR /workspace
