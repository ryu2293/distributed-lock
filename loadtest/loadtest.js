import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

// run-benchmark.sh 가 setup 응답에서 만들어주는 학생 id 목록
const studentIds = new SharedArray('students', () => JSON.parse(open('./students.json')));

const LOCK    = __ENV.LOCK    || 'redisson';   // none | sync | pess | lettuce | redisson | aop
const LECTURE = __ENV.LECTURE || '1';
const PORTS   = ['8080', '8081'];              // 두 인스턴스에 분산

export const options = {
  scenarios: {
    enroll: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 200),           // 동시 가상 유저 수
      iterations: studentIds.length,           // 학생 1명당 신청 1번
      maxDuration: '3m',
    },
  },
};

export default function () {
  const i = exec.scenario.iterationInTest;     // 0..N-1 전역 고유 인덱스
  if (i >= studentIds.length) return;
  const sid  = studentIds[i];
  const port = PORTS[i % 2];
  const res  = http.post(
    `http://localhost:${port}/enrollments?studentId=${sid}&lectureId=${LECTURE}&lock=${LOCK}`
  );
  check(res, { 'status 200': (r) => r.status === 200 });
}
