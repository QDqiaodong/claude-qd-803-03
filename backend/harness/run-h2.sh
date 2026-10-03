#!/usr/bin/env bash
# 换色排程台本地端到端验证（不需要 docker/MySQL）。
# 前提：已经能访问 Maven（~/.m2 有依赖），或镜像构建已预热过依赖。
# 用法：bash harness/run-h2.sh           启动 H2 验证实例后跑 e2e
#       bash harness/run-h2.sh --no-e2e  只启动实例（:8399）
set -euo pipefail
cd "$(dirname "$0")/.."

HARNESS=harness
BUILD=/tmp/mill-h2-build
H2_VER=2.2.224

# 1) 编译主代码到独立目录（不污染 target、不加 test 依赖）
mvn -s settings.xml -B -q dependency:build-classpath -Dmdep.outputFile=/tmp/mill-cp.txt
mkdir -p "$BUILD"
javac -parameters -encoding UTF-8 -cp "$(cat /tmp/mill-cp.txt)" -d "$BUILD" $(find src/main/java -name '*.java')

# 2) 取 H2 jar（仅 harness 用，不进 pom / docker 构建）
mkdir -p /tmp/mill-h2-lib
H2_JAR=/tmp/mill-h2-lib/h2-${H2_VER}.jar
if [ ! -f "$H2_JAR" ]; then
  curl -fsSL -o "$H2_JAR" "https://maven.aliyun.com/repository/public/com/h2database/h2/${H2_VER}/h2-${H2_VER}.jar"
fi

# 3) 编译 harness 启动类 + 引擎夹具
javac -parameters -encoding UTF-8 -cp "$BUILD:$(cat /tmp/mill-cp.txt):$H2_JAR" -d "$BUILD" \
  "$HARNESS/H2TestApplication.java" "$HARNESS/EngineHarness.java"

# 4) 纯引擎验收（无 Spring、无数据库）
echo "== 引擎夹具验收 =="
java -Duser.timezone=Asia/Shanghai -cp "$BUILD" EngineHarness

if [ "${1:-}" = "--no-e2e" ]; then
  echo
  echo "启动接口验证实例（Ctrl+C 结束）："
  exec java -Duser.timezone=Asia/Shanghai -cp "$BUILD:$(cat /tmp/mill-cp.txt):$H2_JAR" \
    -Dspring.profiles.active=h2 \
    -Dspring.config.additional-location=file:$(pwd)/$HARNESS/application-h2.yml \
    com.dyeing.mill.H2TestApplication
fi

# 5) 启动 Spring Boot H2 实例 → 跑 e2e → 停掉
java -Duser.timezone=Asia/Shanghai -cp "$BUILD:$(cat /tmp/mill-cp.txt):$H2_JAR" \
  -Dspring.profiles.active=h2 \
  -Dspring.config.additional-location=file:$(pwd)/$HARNESS/application-h2.yml \
  com.dyeing.mill.H2TestApplication > /tmp/mill-h2-app.log 2>&1 &
APP_PID=$!
trap 'kill $APP_PID 2>/dev/null || true' EXIT

for i in $(seq 1 40); do
  if curl -sf http://127.0.0.1:8399/actuator/health >/dev/null 2>&1; then break; fi
  sleep 0.5
done
echo
echo "== 接口端到端验收 =="
bash "$HARNESS/e2e.sh"
