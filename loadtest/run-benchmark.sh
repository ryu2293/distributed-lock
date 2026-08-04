#!/usr/bin/env bash
# 다중 인스턴스 분산 락 부하테스트 러너
#
# 사전 준비:
#   1) docker compose up -d                                  # MySQL(3307) + Redis(6379)
#   2) ./gradlew bootJar
#   3) 앱 2대 기동:
#      java -jar build/libs/lock-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql --server.port=8080 &
#      java -jar build/libs/lock-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql --server.port=8081 &
#
# 사용법:
#   ./run-benchmark.sh <lock> [N] [VUS]
#   예) ./run-benchmark.sh redisson 3000 200
#   lock: none | sync | pess | lettuce | redisson | aop
set -euo pipefail

LOCK="${1:-redisson}"
N="${2:-3000}"
VUS="${3:-200}"
DIR="$(cd "$(dirname "$0")" && pwd)"

echo "▶ setup: 정원=$N, 학생=$N"
RESP=$(curl -s -X POST "http://localhost:8080/setup?capacity=$N&studentCount=$N")
LID=$(echo "$RESP" | grep -oE '"lectureId":[0-9]+' | grep -oE '[0-9]+')
echo "$RESP" | python3 -c 'import sys,json;json.dump(json.load(sys.stdin)["studentIds"], open("'"$DIR"'/students.json","w"))'
echo "  lectureId=$LID"

echo "▶ k6 run: LOCK=$LOCK VUS=$VUS"
cd "$DIR"
LOCK="$LOCK" LECTURE="$LID" VUS="$VUS" k6 run loadtest.js

echo "▶ 결과:"
curl -s "http://localhost:8080/lectures/$LID/result"; echo
