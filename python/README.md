# ORDA Python Data Pipeline

## 폴더 구조

- data/raw/osm: OSM 원본 데이터
- data/raw/forest: 산림청 원본 데이터
- data/raw/dem: DEM 원본 데이터

- data/mock: 목데이터
- data/interim/a_output: A 담당 중간 산출물
- data/interim/b_output: B 담당 중간 산출물
- data/interim/c_output: C 담당 중간 산출물
- data/final: 최종 데이터셋

- scripts/common: 공통 함수
- scripts/a_collect: A 담당 스크립트
- scripts/b_normalize: B 담당 스크립트
- scripts/c_analyze: C 담당 스크립트

- output/logs: 실행 로그
- output/reports: 검증 리포트
- output/previews: 시각화 결과
- output/temp: 임시 파일